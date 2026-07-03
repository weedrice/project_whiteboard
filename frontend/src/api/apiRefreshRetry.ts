import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { unwrapApiData } from '@/api/response'
import {
  applyRefreshedAccessToken,
  clearExpiredAuthSession,
  enqueueFailedRequest,
  isRefreshInProgress,
  notifySessionExpired,
  processRefreshQueue,
  resetSessionExpiredToastDebounce,
  setRefreshInProgress,
} from '@/api/authRefreshSession'
import type { SuppressibleApiError } from '@/api/errorHandling'
import {
  resolveAuthStore,
  resolveToastStore,
} from '@/api/apiStoreResolvers'
import { API_PATHS } from '@/api/apiPaths'
import { isLoginPathname } from '@/api/apiAuthHeader'
import type { ApiResponse } from '@/types/common'

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

  if (isRefreshInProgress()) {
    return new Promise<string | null>((resolve, reject) => {
      enqueueFailedRequest({ resolve, reject })
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
  setRefreshInProgress(true)

  try {
    const { data } = await axios.post<ApiResponse<RefreshTokenResponse>>(
      `${api.defaults.baseURL}${API_PATHS.REFRESH}`,
      undefined,
      {
        withCredentials: true,
      },
    )

    if (!data.success) {
      throw new Error('Refresh failed')
    }

    const refreshedAccessToken = unwrapApiData(data).accessToken
    lastRefreshFailureAt = 0
    resetSessionExpiredToastDebounce()

    const authStore = await resolveAuthStore()
    const newAccessToken = applyRefreshedAccessToken(authStore, refreshedAccessToken)
    if (!newAccessToken) {
      throw new Error('Refresh returned an invalid access token')
    }

    if (authStore) {
      const didFetchUser = await authStore.fetchUser({ skipAuthRefresh: true })
      if (!didFetchUser) {
        const hydrationError = new Error('User hydration failed after token refresh') as SuppressibleApiError
        hydrationError.isUserHydrationFailure = true
        throw hydrationError
      }
    }

    processRefreshQueue(null, newAccessToken)

    if (originalRequest.headers) {
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
    }
    return api(originalRequest)
  } catch (refreshError) {
    markRefreshFailure()
    const suppressibleRefreshError = refreshError as SuppressibleApiError
    suppressibleRefreshError.suppressGlobalErrorToast = true
    suppressibleRefreshError.isAuthRefreshFailure = true

    processRefreshQueue(suppressibleRefreshError, null)

    const axiosRefreshError = refreshError as AxiosError
    const refreshStatus = axiosRefreshError.response?.status
    const isLoginPage = isLoginPathname()

    if ((refreshStatus === 401 || refreshStatus === 403 || !axiosRefreshError.response) && !suppressibleRefreshError.isUserHydrationFailure) {
      const authStore = await resolveAuthStore()
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
    setRefreshInProgress(false)
  }
}
