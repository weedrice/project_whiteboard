export const AUTH_REFRESH_CHANNEL = 'noviis-auth-session'
export const AUTH_REFRESH_STORAGE_EVENT_KEY = 'noviisAuthRefreshEvent'
export const AUTH_REFRESH_SESSION_KEY = 'noviisAuthRefreshSession'
export const STORAGE_UNAVAILABLE_SESSION_ID = 'shared-origin-cookie-session'

export type RefreshMessage = {
  type: 'refresh-request' | 'refresh-result' | 'refresh-error' | 'refresh-cancelled'
  sourceId: string
  requestId?: string
  previousToken?: string | null
  sessionId?: string
  accessToken?: string
  message?: string
  at: number
}

export function createAuthRefreshId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function isRefreshMessage(value: unknown): value is RefreshMessage {
  if (!value || typeof value !== 'object') return false
  const type = (value as { type?: unknown }).type
  return type === 'refresh-request' || type === 'refresh-result'
    || type === 'refresh-error' || type === 'refresh-cancelled'
}

function readSharedAuthSessionId(): string | null {
  if (typeof localStorage === 'undefined') return null
  try {
    const value = localStorage.getItem(AUTH_REFRESH_SESSION_KEY)
    return value && value.length <= 128 ? value : null
  } catch {
    return null
  }
}

export function getOrCreateSharedAuthSessionId(): string {
  if (typeof localStorage === 'undefined') return STORAGE_UNAVAILABLE_SESSION_ID
  const existing = readSharedAuthSessionId()
  if (existing) return existing
  const created = createAuthRefreshId()
  try {
    localStorage.setItem(AUTH_REFRESH_SESSION_KEY, created)
    return readSharedAuthSessionId() ?? created
  } catch {
    // The HttpOnly refresh cookie is origin-wide, so tabs share one fallback scope.
    return STORAGE_UNAVAILABLE_SESSION_ID
  }
}

export function rotateSharedAuthSessionId(): string {
  const next = createAuthRefreshId()
  try {
    localStorage.setItem(AUTH_REFRESH_SESSION_KEY, next)
  } catch {
    // Storage can be unavailable; the current tab still rotates its local boundary.
  }
  return next
}
