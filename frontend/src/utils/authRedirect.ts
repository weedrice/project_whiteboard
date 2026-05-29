import type { AxiosError } from 'axios'
import { extractErrorResponse } from '@/utils/errorHandler'

export const LOGIN_REDIRECT_KEY = 'loginRedirect'
export const DELETED_ACCOUNT_ERROR_CODE = 'A009'
export const DELETED_ACCOUNT_MESSAGE_KEY = 'auth.userDeleted'

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

interface DeletedAccountRedirectOptions {
  email: string
  t: (key: string) => string
  addToast: (message: string, type: 'info') => void
  push: (to: string) => unknown
}

export function isDeletedAccountError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }

  if (extractErrorResponse(error as AxiosError)?.code === DELETED_ACCOUNT_ERROR_CODE) {
    return true
  }

  const responseData = (error as AxiosError | undefined)?.response?.data as { error?: { code?: string }, code?: string } | undefined
  return (responseData?.error?.code ?? responseData?.code) === DELETED_ACCOUNT_ERROR_CODE
}

export function buildDeletedAccountSignupPath(email: string): string {
  return `/signup?email=${encodeURIComponent(email.trim())}`
}

export function handleDeletedAccountRedirect(error: unknown, options: DeletedAccountRedirectOptions): boolean {
  if (!isDeletedAccountError(error)) {
    return false
  }

  options.addToast(options.t(DELETED_ACCOUNT_MESSAGE_KEY), 'info')
  options.push(buildDeletedAccountSignupPath(options.email))
  return true
}
