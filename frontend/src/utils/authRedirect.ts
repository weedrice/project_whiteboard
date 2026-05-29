export const LOGIN_REDIRECT_KEY = 'loginRedirect'

export function isSafeRedirect(path: unknown): path is string {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')
}

export function saveLoginRedirect(path: unknown): void {
  if (isSafeRedirect(path)) {
    sessionStorage.setItem(LOGIN_REDIRECT_KEY, path)
  }
}

export function getStoredLoginRedirect(): string | null {
  const redirect = sessionStorage.getItem(LOGIN_REDIRECT_KEY)
  return isSafeRedirect(redirect) ? redirect : null
}

export function clearLoginRedirect(): void {
  sessionStorage.removeItem(LOGIN_REDIRECT_KEY)
}

export function resolveLoginRedirect(queryRedirect: unknown, fallback = '/'): string {
  if (isSafeRedirect(queryRedirect)) return queryRedirect
  return getStoredLoginRedirect() ?? fallback
}
