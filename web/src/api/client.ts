import type { LoginResponse, ProblemDetail } from './types'

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const REFRESH_KEY = 'gk.refreshToken'

let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null

export class ApiError extends Error {
  status: number
  body?: ProblemDetail
  constructor(status: number, message: string, body?: ProblemDetail) {
    super(message)
    this.status = status
    this.body = body
  }
}

export const tokens = {
  get access() { return accessToken },
  get refresh() { try { return localStorage.getItem(REFRESH_KEY) } catch { return null } },
  set(login: LoginResponse) {
    accessToken = login.accessToken
    try { localStorage.setItem(REFRESH_KEY, login.refreshToken) } catch { /* ignore */ }
  },
  clear() {
    accessToken = null
    try { localStorage.removeItem(REFRESH_KEY) } catch { /* ignore */ }
  },
}

/** Decoded JWT payload (sub, tenant, attrs, exp). Not verified — display only. */
export function decodeJwt(token: string): Record<string, unknown> {
  try {
    const [, payload] = token.split('.')
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch { return {} }
}

async function parseBody(res: Response): Promise<unknown> {
  if (res.status === 204) return undefined
  const text = await res.text()
  if (!text) return undefined
  try { return JSON.parse(text) } catch { return text }
}

async function tryRefresh(): Promise<boolean> {
  const rt = tokens.refresh
  if (!rt) return false
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt }),
    })
      .then(async res => {
        if (!res.ok) { tokens.clear(); return false }
        tokens.set(await res.json() as LoginResponse)
        return true
      })
      .catch(() => false)
      .finally(() => { refreshInFlight = null })
  }
  return refreshInFlight
}

export interface RequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  auth?: boolean
  query?: Record<string, string | undefined>
}

export async function request<T>(path: string, opts: RequestOptions = {}, retried = false): Promise<T> {
  const { method = 'GET', body, headers = {}, auth = true, query } = opts
  const qs = query
    ? '?' + Object.entries(query).filter(([, v]) => v !== undefined && v !== '')
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v!)}`).join('&')
    : ''
  const h: Record<string, string> = { ...headers }
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (auth && accessToken) h['Authorization'] = `Bearer ${accessToken}`

  const res = await fetch(`${BASE}${path}${qs}`, {
    method,
    headers: h,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && auth && !retried && await tryRefresh()) {
    return request<T>(path, opts, true)
  }

  const parsed = await parseBody(res)
  if (!res.ok) {
    const pd = (typeof parsed === 'object' && parsed) ? parsed as ProblemDetail : undefined
    const msg = pd?.detail || pd?.title || (typeof parsed === 'string' ? parsed : res.statusText) || `HTTP ${res.status}`
    throw new ApiError(res.status, msg, pd)
  }
  return parsed as T
}

/** Warm the access token from a stored refresh token on page load. */
export async function restoreSession(): Promise<boolean> {
  if (accessToken) return true
  return tryRefresh()
}
