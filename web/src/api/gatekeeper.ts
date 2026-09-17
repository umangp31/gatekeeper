import { request } from './client'
import type { EvaluateResponse, Flag, LoginResponse, Permission, Role, Tenant, User } from './types'

export const auth = {
  login: (tenantSlug: string, email: string, password: string) =>
    request<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: { tenantSlug, email, password }, auth: false }),
  logout: (refreshToken: string) =>
    request<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
}

export const tenants = {
  bootstrap: (body: { slug: string; name: string; adminEmail: string; adminPassword: string }, bootstrapToken: string) =>
    request<Tenant>('/api/v1/tenants', { method: 'POST', body, auth: false, headers: { 'X-Bootstrap-Token': bootstrapToken } }),
  me: () => request<Tenant>('/api/v1/tenants/me'),
  rename: (name: string) => request<Tenant>('/api/v1/tenants/me', { method: 'PATCH', body: { name } }),
}

export const users = {
  list: () => request<User[]>('/api/v1/users'),
  get: (id: string) => request<User>(`/api/v1/users/${id}`),
  create: (email: string, password: string) => request<User>('/api/v1/users', { method: 'POST', body: { email, password } }),
  updateAttributes: (id: string, attributes: Record<string, unknown>) =>
    request<User>(`/api/v1/users/${id}/attributes`, { method: 'PATCH', body: { attributes } }),
  permissions: (id: string) => request<string[]>(`/api/v1/users/${id}/permissions`),
  assignRole: (id: string, roleId: string) => request<void>(`/api/v1/users/${id}/roles`, { method: 'POST', body: { roleId } }),
  revokeRole: (id: string, roleId: string) => request<void>(`/api/v1/users/${id}/roles/${roleId}`, { method: 'DELETE' }),
}

export const roles = {
  list: () => request<Role[]>('/api/v1/roles'),
  get: (id: string) => request<Role>(`/api/v1/roles/${id}`),
  create: (name: string, description?: string) => request<Role>('/api/v1/roles', { method: 'POST', body: { name, description } }),
  update: (id: string, description: string) => request<Role>(`/api/v1/roles/${id}`, { method: 'PATCH', body: { description } }),
  remove: (id: string) => request<void>(`/api/v1/roles/${id}`, { method: 'DELETE' }),
  grant: (id: string, code: string) => request<void>(`/api/v1/roles/${id}/permissions/${encodeURIComponent(code)}`, { method: 'POST' }),
  revoke: (id: string, code: string) => request<void>(`/api/v1/roles/${id}/permissions/${encodeURIComponent(code)}`, { method: 'DELETE' }),
  addParent: (id: string, parentId: string) => request<void>(`/api/v1/roles/${id}/parents/${parentId}`, { method: 'POST' }),
  removeParent: (id: string, parentId: string) => request<void>(`/api/v1/roles/${id}/parents/${parentId}`, { method: 'DELETE' }),
  catalog: () => request<Permission[]>('/api/v1/permissions'),
}

export const flags = {
  list: () => request<Flag[]>('/api/v1/flags'),
  get: (key: string) => request<Flag>(`/api/v1/flags/${key}`),
  create: (flagKey: string, description?: string) => request<Flag>('/api/v1/flags', { method: 'POST', body: { flagKey, description } }),
  update: (key: string, body: { enabled?: boolean; rolloutPercentage?: number; description?: string; version: number }) =>
    request<Flag>(`/api/v1/flags/${key}`, { method: 'PUT', body }),
  remove: (key: string) => request<void>(`/api/v1/flags/${key}`, { method: 'DELETE' }),
  whitelist: (key: string, userId: string) => request<void>(`/api/v1/flags/${key}/whitelist/${userId}`, { method: 'PUT' }),
  unwhitelist: (key: string, userId: string) => request<void>(`/api/v1/flags/${key}/whitelist/${userId}`, { method: 'DELETE' }),
  setOverride: (key: string, env: string, enabled: boolean) =>
    request<void>(`/api/v1/flags/${key}/overrides/${env}`, { method: 'PUT', body: { enabled } }),
  removeOverride: (key: string, env: string) => request<void>(`/api/v1/flags/${key}/overrides/${env}`, { method: 'DELETE' }),
  evaluate: (key: string, userId?: string) => request<EvaluateResponse>(`/api/v1/flags/${key}/evaluate`, { method: 'POST', query: { userId } }),
  evaluateAll: () => request<Record<string, boolean>>('/api/v1/flags/evaluate'),
}
