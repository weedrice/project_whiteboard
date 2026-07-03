import type { AxiosRequestConfig } from 'axios'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'

export const SESSION_EXPIRED_TOAST_DEBOUNCE_MS = 5000

export interface ToastStore {
  addToast: (message: string, type?: 'info' | 'success' | 'warning' | 'error', duration?: number, position?: 'top-center' | 'bottom-center') => void
}

export interface AuthStoreLike {
  fetchUser: (config?: AxiosRequestConfig) => Promise<boolean>
  setTokens: (token: string) => void
  clearSessionState: () => void
}

interface FailedRequest {
  resolve: (token: string | null) => void
  reject: (error: unknown) => void
}

let isRefreshing = false
let failedQueue: FailedRequest[] = []
let lastSessionExpiredToastAt = 0

export const isRefreshInProgress = () => isRefreshing

export const setRefreshInProgress = (value: boolean) => {
  isRefreshing = value
}

export const enqueueFailedRequest = (request: FailedRequest) => {
  failedQueue.push(request)
}

export const processRefreshQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((request) => {
    if (error) {
      request.reject(error)
    } else {
      request.resolve(token)
    }
  })
  failedQueue = []
}

export const resetSessionExpiredToastDebounce = () => {
  lastSessionExpiredToastAt = 0
}

export const notifySessionExpired = (toastStore: ToastStore, message: string) => {
  const now = Date.now()
  if (now - lastSessionExpiredToastAt < SESSION_EXPIRED_TOAST_DEBOUNCE_MS) {
    return
  }

  lastSessionExpiredToastAt = now
  toastStore.addToast(message, 'warning', 3000, 'top-center')
}

export const applyRefreshedAccessToken = (authStore: AuthStoreLike | null, token: unknown): string | null => {
  if (typeof token !== 'string' || token.length === 0) {
    return null
  }

  if (authStore) {
    authStore.setTokens(token)
  } else {
    persistAccessToken(token)
  }

  return token
}

export const clearExpiredAuthSession = (authStore: AuthStoreLike | null) => {
  if (authStore) {
    authStore.clearSessionState()
    return
  }

  clearStoredAuthTokens()
}
