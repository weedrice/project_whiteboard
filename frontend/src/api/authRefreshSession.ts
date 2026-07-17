import type { AxiosRequestConfig } from 'axios'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'

export const SESSION_EXPIRED_TOAST_DEBOUNCE_MS = 5000

export interface ToastStore {
  addToast: (message: string, type?: 'info' | 'success' | 'warning' | 'error', duration?: number, position?: 'top-center' | 'bottom-center') => void
}

export interface AuthStoreLike {
  accessToken: string | null
  sessionGeneration: number
  fetchUser: (config?: AxiosRequestConfig) => Promise<boolean>
  setTokens: (token: string) => void
  applyTokenIfCurrent: (generation: number, previousToken: string | null, token: string) => boolean
  clearSessionState: () => void
}

interface FailedRequest {
  generation: number
  resolve: (token: string | null) => void
  reject: (error: unknown) => void
}

let isRefreshing = false
let refreshOperation = 0
let failedQueue: FailedRequest[] = []
let lastSessionExpiredToastAt = 0

export class AuthSessionChangedError extends Error {
  constructor() {
    super('Authentication session changed while refresh was in progress')
    this.name = 'AuthSessionChangedError'
  }
}

export const isRefreshInProgress = () => isRefreshing

export const setRefreshInProgress = (value: boolean) => {
  isRefreshing = value
}

export const beginAuthRefresh = () => {
  refreshOperation += 1
  isRefreshing = true
  return refreshOperation
}

export const finishAuthRefresh = (operation: number) => {
  if (operation === refreshOperation) {
    isRefreshing = false
  }
}

export const enqueueFailedRequest = (request: FailedRequest) => {
  failedQueue.push(request)
}

export const processRefreshQueue = (error: unknown, token: string | null = null, generation?: number) => {
  failedQueue.forEach((request) => {
    if (error || (generation !== undefined && request.generation !== generation)) {
      request.reject(error ?? new AuthSessionChangedError())
    } else {
      request.resolve(token)
    }
  })
  failedQueue = []
}

export const cancelPendingAuthRefresh = () => {
  refreshOperation += 1
  const error = new AuthSessionChangedError()
  processRefreshQueue(error)
  isRefreshing = false
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

export const applyRefreshedAccessToken = (
  authStore: AuthStoreLike | null,
  token: unknown,
  generation?: number,
  previousToken?: string | null,
): string | null => {
  if (typeof token !== 'string' || token.length === 0) {
    return null
  }

  if (authStore) {
    if (generation !== undefined && previousToken !== undefined) {
      if (authStore.applyTokenIfCurrent(generation, previousToken, token)) return token
      return authStore.sessionGeneration === generation && authStore.accessToken === token ? token : null
    }
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
