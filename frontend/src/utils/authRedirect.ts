import { SessionStorage } from '@/utils/storage'

export const LOGIN_REDIRECT_KEY = 'loginRedirect'
export const LOGIN_REDIRECT_TTL_MS = 10 * 60 * 1000
export const DELETED_ACCOUNT_ERROR_CODE = 'A009'
export const DELETED_ACCOUNT_MESSAGE_KEY = 'auth.userDeleted'

interface LoginRedirectIntent {
  path: string
  expiresAt: number
  nonce: string
}

export function isSafeRedirect(path: unknown): path is string {
  if (typeof path !== 'string'
    || !path.startsWith('/')
    || path.startsWith('//')
    || path.includes('\\')
    || [...path].some((character) => character.charCodeAt(0) <= 31 || character.charCodeAt(0) === 127)) {
    return false
  }
  let decoded = path
  try {
    for (let index = 0; index < 3; index += 1) {
      const next = decodeURIComponent(decoded)
      if (next === decoded) break
      decoded = next
    }
  } catch {
    return false
  }
  return decoded.startsWith('/') && !decoded.startsWith('//') && !decoded.includes('\\')
}

function createNonce(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function saveLoginRedirect(path: unknown, now = Date.now()): string | null {
  if (!isSafeRedirect(path)) return null
  const nonce = createNonce()
  SessionStorage.setString(LOGIN_REDIRECT_KEY, JSON.stringify({
    path,
    expiresAt: now + LOGIN_REDIRECT_TTL_MS,
    nonce,
  } satisfies LoginRedirectIntent))
  return nonce
}

export function getStoredLoginRedirect(now = Date.now()): string | null {
  const rawIntent = SessionStorage.getString(LOGIN_REDIRECT_KEY)
  let intent: LoginRedirectIntent | null = null
  try {
    intent = rawIntent ? JSON.parse(rawIntent) as LoginRedirectIntent : null
  } catch {
    intent = null
  }
  if (!intent || !isSafeRedirect(intent.path) || !intent.nonce || intent.expiresAt <= now) {
    clearLoginRedirect()
    return null
  }
  return intent.path
}

export function clearLoginRedirect(): void {
  SessionStorage.remove(LOGIN_REDIRECT_KEY)
}

export function consumeLoginRedirect(now = Date.now()): string | null {
  const redirect = getStoredLoginRedirect(now)
  clearLoginRedirect()
  return redirect
}

export function resolveLoginRedirect(queryRedirect: unknown, fallback = '/'): string {
  if (isSafeRedirect(queryRedirect)) return queryRedirect
  return consumeLoginRedirect() ?? fallback
}

export function shouldExitProtectedRouteAfterSessionLoss(
  isAuthenticated: boolean,
  wasAuthenticated: boolean | undefined,
  requiresAuth: boolean | undefined,
): boolean {
  return wasAuthenticated === true && !isAuthenticated && requiresAuth === true
}

interface DeletedAccountRedirectOptions {
  email: string
  t: (key: string) => string
  addToast: (message: string, type: 'info') => void
  push: (to: string) => unknown
}

export function isDeletedAccountError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const responseData = (error as { response?: { data?: { error?: { code?: string }, code?: string } } }).response?.data
  return (responseData?.error?.code ?? responseData?.code) === DELETED_ACCOUNT_ERROR_CODE
}

export function buildDeletedAccountSignupPath(email: string): string {
  return `/signup?email=${encodeURIComponent(email.trim())}`
}

export function handleDeletedAccountRedirect(error: unknown, options: DeletedAccountRedirectOptions): boolean {
  if (!isDeletedAccountError(error)) return false
  options.addToast(options.t(DELETED_ACCOUNT_MESSAGE_KEY), 'info')
  options.push(buildDeletedAccountSignupPath(options.email))
  return true
}
