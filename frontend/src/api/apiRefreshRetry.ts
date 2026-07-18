import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { unwrapApiData } from '@/api/response'
import {
  applyRefreshedAccessToken,
  AuthSessionChangedError,
  beginAuthRefresh,
  enqueueFailedRequest,
  isRefreshInProgress,
  processRefreshQueue,
  resetSessionExpiredToastDebounce,
  finishAuthRefresh,
} from '@/api/authRefreshSession'
import type { SuppressibleApiError } from '@/api/errorHandling'
import { resolveAuthStore } from '@/api/apiStoreResolvers'
import { API_PATHS } from '@/api/apiPaths'
import { API } from '@/utils/constants'
import type { ApiResponse } from '@/types/common'
import { coordinateAuthRefresh } from '@/api/authRefreshCoordinator'
import { isStampedAuthContextCurrent } from '@/api/apiAuthHeader'
import { getCurrentSessionGeneration } from '@/queryAuthScope'
import { getStoredAccessToken } from '@/utils/authTokenStorage'
import { handleTerminalAuthFailure } from '@/api/authTerminalFailure'

type RefreshTokenResponse = {
  accessToken: string
}

const REFRESH_FAILURE_COOLDOWN_MS = 10_000
type RefreshFailureContext = {
  generation: number
  accessToken: string | null
  failedAt: number
}

let lastRefreshFailure: RefreshFailureContext | null = null

export function resetAuthRefreshFailureCooldownForTest() {
  lastRefreshFailure = null
}

function isRefreshInCooldown(generation: number, accessToken: string | null): boolean {
  return lastRefreshFailure?.generation === generation
    && lastRefreshFailure.accessToken === accessToken
    && Date.now() - lastRefreshFailure.failedAt < REFRESH_FAILURE_COOLDOWN_MS
}

function markRefreshFailure(generation: number, accessToken: string | null) {
  lastRefreshFailure = { generation, accessToken, failedAt: Date.now() }
}

function createSuppressedSessionChangedError() {
  const error = new AuthSessionChangedError() as SuppressibleApiError
  error.suppressGlobalErrorToast = true
  error.isAuthRefreshFailure = true
  return error
}

function isRefreshSessionCurrent(
  authStore: Awaited<ReturnType<typeof resolveAuthStore>>,
  generation: number,
  accessToken: string | null,
) {
  return authStore
    ? authStore.sessionGeneration === generation && authStore.accessToken === accessToken
    : getCurrentSessionGeneration() === generation && getStoredAccessToken() === accessToken
}

export async function retryAfterRefresh(api: AxiosInstance, originalRequest: InternalAxiosRequestConfig) {
  const authStore = await resolveAuthStore()
  const generation = authStore?.sessionGeneration ?? getCurrentSessionGeneration()
  const previousToken = authStore?.accessToken ?? getStoredAccessToken()
  const expectedUserId = authStore?.user?.userId ?? authStore?.user?.id ?? null
  if (isRefreshInCooldown(generation, previousToken)) {
    const cooldownError = new Error('Refresh temporarily unavailable') as SuppressibleApiError
    cooldownError.suppressGlobalErrorToast = true
    cooldownError.isAuthRefreshFailure = true
    return Promise.reject(cooldownError)
  }
  if (!isStampedAuthContextCurrent(originalRequest, generation, previousToken)) {
    return Promise.reject(createSuppressedSessionChangedError())
  }

  if (isRefreshInProgress()) {
    originalRequest._retry = true
    return new Promise<string | null>((resolve, reject) => {
      enqueueFailedRequest({ generation, resolve, reject })
    })
      .then((token) => {
        if (!token || !isRefreshSessionCurrent(authStore, generation, token)) {
          throw createSuppressedSessionChangedError()
        }
        if (originalRequest.headers && token) {
          originalRequest.headers.Authorization = `Bearer ${token}`
        }
        return api(originalRequest)
      })
      .catch((err) => Promise.reject(err))
  }

  originalRequest._retry = true
  const refreshOperation = beginAuthRefresh()

  try {
    const refreshedAccessToken = await coordinateAuthRefresh(async (signal) => {
      if (authStore && (authStore.sessionGeneration !== generation || authStore.accessToken !== previousToken)) {
        throw new AuthSessionChangedError()
      }
      const { data } = await axios.post<ApiResponse<RefreshTokenResponse>>(
        `${api.defaults.baseURL}${API_PATHS.REFRESH}`,
        undefined,
        {
          withCredentials: true,
          timeout: api.defaults.timeout ?? API.TIMEOUT,
          signal,
        },
      )
      if (!data.success) throw new Error('Refresh failed')
      return unwrapApiData(data).accessToken
    }, { previousToken })
    lastRefreshFailure = null
    resetSessionExpiredToastDebounce()

    const newAccessToken = applyRefreshedAccessToken(
      authStore,
      refreshedAccessToken,
      generation,
      previousToken,
    )
    if (!newAccessToken) {
      throw new AuthSessionChangedError()
    }

    if (authStore) {
      const didFetchUser = await authStore.fetchUser({ skipAuthRefresh: true }, expectedUserId)
      if (!didFetchUser) {
        const hydrationError = new Error('User hydration failed after token refresh') as SuppressibleApiError
        hydrationError.isUserHydrationFailure = true
        throw hydrationError
      }
    }

    if (!isRefreshSessionCurrent(authStore, generation, newAccessToken)) {
      throw new AuthSessionChangedError()
    }

    processRefreshQueue(null, newAccessToken, generation)

    if (originalRequest.headers) {
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
    }
    return api(originalRequest)
  } catch (refreshError) {
    const sessionChanged = refreshError instanceof AuthSessionChangedError
      || (authStore !== null && authStore.sessionGeneration !== generation)
    if (!sessionChanged) markRefreshFailure(generation, previousToken)
    const suppressibleRefreshError = refreshError as SuppressibleApiError
    suppressibleRefreshError.suppressGlobalErrorToast = true
    suppressibleRefreshError.isAuthRefreshFailure = true

    processRefreshQueue(suppressibleRefreshError, null, generation)

    const axiosRefreshError = refreshError as AxiosError
    const refreshStatus = axiosRefreshError.response?.status
    if (!sessionChanged && !suppressibleRefreshError.isUserHydrationFailure) {
      await handleTerminalAuthFailure(refreshStatus ?? null, authStore, {
        generation,
        accessToken: previousToken,
      })
    }
    return Promise.reject(suppressibleRefreshError)
  } finally {
    finishAuthRefresh(refreshOperation)
  }
}
