export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}
export interface Tenant { id: string; slug: string; name: string; status: string }
export interface User { id: string; email: string; enabled: boolean; attributes: Record<string, unknown> }
export interface Role { id: string; name: string; description: string | null }
export interface Permission { id: string; code: string; description: string }
export interface Flag {
  id: string
  flagKey: string
  enabled: boolean
  rolloutPercentage: number
  description: string | null
  version: number
}
export interface EvaluateResponse { flagKey: string; enabled: boolean }
export interface ProblemDetail { status: number; title?: string; detail?: string }
