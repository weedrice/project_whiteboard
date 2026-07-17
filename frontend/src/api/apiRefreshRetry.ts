import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { unwrapApiData } from '@/api/response'
import {
  applyRefreshedAccessToken,
  AuthSessionChangedError,
  beginAuthRefresh,
  clearExpiredAuthSession,
  enqueueFailedRequest,
  isRefreshInProgress,
  notifySessionExpired,
  processRefreshQueue,
  resetSessionExpiredToastDebounce,
  finishAuthRefresh,
} from '@/api/authRefreshSession'
import type { SuppressibleApiError } from '@/api/errorHandling'
import {
  resolveAuthStore,
  resolveToastStore,
} from '@/api/apiStoreResolvers'
import { API_PATHS } from '@/api/apiPaths'
import { isLoginPathname } from '@/api/apiAuthHeader'
import { API } from '@/utils/constants'
import type { ApiResponse } from '@/types/common'
import { coordinateAuthRefresh } from '@/api/authRefreshCoordinator'

const { t } = i18n.global

type RefreshTokenResponse = {
  accessToken: string
}

const REFRESH_FAILURE_COOLDOWN_MS = 10_000
let lastRefreshFailureAt = 0

function isRefreshInCooldown(): boolean {
  return lastRefreshFailureAt > 0 && Date.now() - lastRefreshFailureAt < REFRESH_FAILURE_COOLDOWN_MS
}

function markRefreshFailure() {
  lastRefreshFailureAt = Date.now()
}

export async function retryAfterRefresh(api: AxiosInstance, originalRequest: InternalAxiosRequestConfig) {
  if (isRefreshInCooldown()) {
    const cooldownError = new Error('Refresh temporarily unavailable') as SuppressibleApiError
    cooldownError.suppressGlobalErrorToast = true
    cooldownError.isAuthRefreshFailure = true
    return Promise.reject(cooldownError)
  }

  const authStore = await resolveAuthStore()
  const generation = authStore?.sessionGeneration ?? -1
  const previousToken = authStore?.accessToken ?? null

  if (isRefreshInProgress()) {
    originalRequest._retry = true
    return new Promise<string | null>((resolve, reject) => {
      enqueueFailedRequest({ generation, resolve, reject })
    })
      .then((token) => {
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
    const refreshedAccessToken = await coordinateAuthRefresh(async () => {
      if (authStore && (authStore.sessionGeneration !== generation || authStore.accessToken !== previousToken)) {
        throw new AuthSessionChangedError()
      }
      const { data } = await axios.post<ApiResponse<RefreshTokenResponse>>(
        `${api.defaults.baseURL}${API_PATHS.REFRESH}`,
        undefined,
        {
          withCredentials: true,
          timeout: api.defaults.timeout ?? API.TIMEOUT,
        },
      )
      if (!data.success) throw new Error('Refresh failed')
      return unwrapApiData(data).accessToken
    })
    lastRefreshFailureAt = 0
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
      const didFetchUser = await authStore.fetchUser({ skipAuthRefresh: true })
      if (!didFetchUser) {
        const hydrationError = new Error('User hydration failed after token refresh') as SuppressibleApiError
        hydrationError.isUserHydrationFailure = true
        throw hydrationError
      }
    }

    processRefreshQueue(null, newAccessToken, generation)

    if (originalRequest.headers) {
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
    }
    return api(originalRequest)
  } catch (refreshError) {
    const sessionChanged = authStore !== null && authStore.sessionGeneration !== generation
    if (!sessionChanged) markRefreshFailure()
    const suppressibleRefreshError = refreshError as SuppressibleApiError
    suppressibleRefreshError.suppressGlobalErrorToast = true
    suppressibleRefreshError.isAuthRefreshFailure = true

    processRefreshQueue(suppressibleRefreshError, null, generation)

    const axiosRefreshError = refreshError as AxiosError
    const refreshStatus = axiosRefreshError.response?.status
    const isLoginPage = isLoginPathname()

    if (!sessionChanged && (refreshStatus === 401 || refreshStatus === 403 || !axiosRefreshError.response) && !suppressibleRefreshError.isUserHydrationFailure) {
      clearExpiredAuthSession(authStore)

      if (!isLoginPage && router.currentRoute.value.meta.requiresAuth) {
        const toastStore = await resolveToastStore()
        notifySessionExpired(toastStore, t('common.messages.sessionExpired'))
        void router.push({
          path: API_PATHS.LOGIN,
          query: { redirect: router.currentRoute.value.fullPath },
        })
      }
    }
    return Promise.reject(suppressibleRefreshError)
  } finally {
    finishAuthRefresh(refreshOperation)
  }
}
