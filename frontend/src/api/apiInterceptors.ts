import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { Storage } from '@/utils/storage'
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
import {
  handleApiError,
  isCanceledRequestError,
  markGlobalErrorToastHandled,
  shouldMarkGlobalErrorToastHandled,
  type ApiErrorResponse,
  type SuppressibleApiError,
} from '@/api/errorHandling'
import {
  noopToastStore,
  resolveAuthStore,
  resolveToastStore,
} from '@/api/apiStoreResolvers'
import { getCurrentPathname as readCurrentPathname } from '@/utils/browserEnv'
import type { ApiResponse } from '@/types/common'

const { t } = i18n.global

const API_PATHS = {
  REFRESH: '/auth/refresh',
  LOGIN: '/login'
}

type RefreshTokenResponse = {
  accessToken: string
}

export const getCurrentPathname = readCurrentPathname

export const isLoginPathname = (pathname = getCurrentPathname()): boolean => pathname === API_PATHS.LOGIN

function applyAuthHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = Storage.getString('accessToken')
  const isAuthEndpoint = config.url?.includes('/auth/')
  const isEmailVerificationApi = config.url?.includes('/auth/email/send-verification') || config.url?.includes('/auth/email/verify')

  if (token && (!isAuthEndpoint || isEmailVerificationApi)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

async function retryAfterRefresh(api: AxiosInstance, originalRequest: InternalAxiosRequestConfig) {
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
    resetSessionExpiredToastDebounce()

    const authStore = await resolveAuthStore()
    const newAccessToken = applyRefreshedAccessToken(authStore, refreshedAccessToken)

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
          query: { redirect: router.currentRoute.value.fullPath }
        })
      }
    }
    return Promise.reject(suppressibleRefreshError)
  } finally {
    setRefreshInProgress(false)
  }
}

function handleGlobalApiError(error: AxiosError, toastStore = noopToastStore) {
  handleApiError(error, toastStore, t)
  if (shouldMarkGlobalErrorToastHandled(error, toastStore, noopToastStore)) {
    markGlobalErrorToastHandled(error)
  }
}

async function handleResponseError(api: AxiosInstance, error: AxiosError) {
  const originalRequest = error.config as InternalAxiosRequestConfig | undefined
  const toastStore = await resolveToastStore()

  if (isCanceledRequestError(error)) {
    return Promise.reject(error)
  }

  if (!originalRequest) {
    handleGlobalApiError(error, toastStore)
    return Promise.reject(error)
  }

  if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh) {
    return retryAfterRefresh(api, originalRequest)
  }

  if (originalRequest.redirectOnError) {
    const status = error.response?.status || 500
    const errorData = error.response?.data as ApiErrorResponse | undefined
    const message = errorData?.message || error.message
    router.push({ name: 'error', query: { status: status.toString(), message } })
    return Promise.reject(error)
  }

  if (originalRequest.skipGlobalErrorHandler) {
    return Promise.reject(error)
  }

  handleGlobalApiError(error, toastStore)
  return Promise.reject(error)
}

export function installApiInterceptors(api: AxiosInstance) {
  api.interceptors.request.use(
    applyAuthHeader,
    (error: AxiosError) => Promise.reject(error)
  )

  api.interceptors.response.use(
    (response: AxiosResponse) => response,
    (error: AxiosError) => handleResponseError(api, error)
  )
}
