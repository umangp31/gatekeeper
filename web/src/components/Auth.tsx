import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { decodeJwt, restoreSession, tokens } from '../api/client'
import { auth as authApi, tenants } from '../api/gatekeeper'
import type { Tenant } from '../api/types'

interface Session {
  userId: string
  tenantId: string
  attrs: Record<string, unknown>
  tenant: Tenant | null
}

interface AuthCtx {
  session: Session | null
  loading: boolean
  login: (slug: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshTenant: () => Promise<void>
}

const Ctx = createContext<AuthCtx>(null!)

function sessionFromToken(): Omit<Session, 'tenant'> | null {
  const t = tokens.access
  if (!t) return null
  const c = decodeJwt(t)
  return { userId: String(c.sub ?? ''), tenantId: String(c.tenant ?? ''), attrs: (c.attrs as Record<string, unknown>) ?? {} }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  const hydrate = useCallback(async () => {
    const base = sessionFromToken()
    if (!base) { setSession(null); return }
    let tenant: Tenant | null = null
    try { tenant = await tenants.me() } catch { /* shown as unknown */ }
    setSession({ ...base, tenant })
  }, [])

  useEffect(() => {
    restoreSession().then(hydrate).finally(() => setLoading(false))
  }, [hydrate])

  const login = useCallback(async (slug: string, email: string, password: string) => {
    tokens.set(await authApi.login(slug, email, password))
    await hydrate()
  }, [hydrate])

  const logout = useCallback(async () => {
    const rt = tokens.refresh
    if (rt) { try { await authApi.logout(rt) } catch { /* ignore */ } }
    tokens.clear()
    setSession(null)
  }, [])

  return <Ctx.Provider value={{ session, loading, login, logout, refreshTenant: hydrate }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
