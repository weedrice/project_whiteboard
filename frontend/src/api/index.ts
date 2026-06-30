import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { Storage } from '@/utils/storage'
import { API } from '@/utils/constants'
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
    type AuthStoreLike,
    type ToastStore,
} from '@/api/authRefreshSession'
import {
    handleApiError,
    isCanceledRequestError,
    markGlobalErrorToastHandled,
    shouldMarkGlobalErrorToastHandled,
    type ApiErrorResponse,
    type SuppressibleApiError,
} from '@/api/errorHandling'

const { t } = i18n.global

// Constants
const API_PATHS = {
    REFRESH: '/auth/refresh',
    LOGIN: '/login'
}

export const getCurrentPathname = (): string => {
    if (typeof window === 'undefined') {
        return ''
    }
    return window.location.pathname
}

export const isLoginPathname = (pathname = getCurrentPathname()): boolean => pathname === API_PATHS.LOGIN

// Extend InternalAxiosRequestConfig to include _retry property
declare module 'axios' {
    export interface AxiosRequestConfig {
        skipGlobalErrorHandler?: boolean;
        skipAuthRefresh?: boolean;
    }
    export interface InternalAxiosRequestConfig {
        _retry?: boolean;
        redirectOnError?: boolean;
        skipGlobalErrorHandler?: boolean;
        skipAuthRefresh?: boolean;
    }
}

import type { ApiResponse } from '@/types/common'

type RefreshTokenResponse = {
    accessToken: string
}

interface ApiStoreResolvers {
    resolveToastStore?: () => ToastStore | Promise<ToastStore>
    resolveAuthStore?: () => AuthStoreLike | null | Promise<AuthStoreLike | null>
}

const noopToastStore: ToastStore = {
    addToast: () => undefined,
}

let toastStoreResolver: ApiStoreResolvers['resolveToastStore'] | null = null
let authStoreResolver: ApiStoreResolvers['resolveAuthStore'] | null = null

export const configureApiStoreResolvers = (resolvers: ApiStoreResolvers): void => {
    if (resolvers.resolveToastStore) {
        toastStoreResolver = resolvers.resolveToastStore
    }
    if (resolvers.resolveAuthStore) {
        authStoreResolver = resolvers.resolveAuthStore
    }
}

const resolveToastStore = async (): Promise<ToastStore> => {
    try {
        if (!toastStoreResolver) {
            return noopToastStore
        }
        return await toastStoreResolver()
    } catch {
        return noopToastStore
    }
}

const resolveAuthStore = async (): Promise<AuthStoreLike | null> => {
    try {
        if (!authStoreResolver) {
            return null
        }
        return await authStoreResolver()
    } catch {
        return null
    }
}

const api: AxiosInstance = axios.create({
    baseURL: API.BASE_URL,
    timeout: API.TIMEOUT,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
})

// Request Interceptor
api.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const token = Storage.getString('accessToken')
        // Skip adding token for auth endpoints to avoid 401s with expired tokens on public endpoints.
        // Email verification endpoints still need the token when called after login.
        const isAuthEndpoint = config.url?.includes('/auth/')
        const isEmailVerificationApi = config.url?.includes('/auth/email/send-verification') || config.url?.includes('/auth/email/verify')

        if (token && (!isAuthEndpoint || isEmailVerificationApi)) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error: AxiosError) => {
        return Promise.reject(error)
    }
)

// Response Interceptor
api.interceptors.response.use(
    (response: AxiosResponse) => {
        return response
    },
    async (error: AxiosError) => {
        const originalRequest = error.config as InternalAxiosRequestConfig | undefined
        const toastStore = await resolveToastStore()

        if (isCanceledRequestError(error)) {
            return Promise.reject(error)
        }

        if (!originalRequest) {
            handleApiError(error, toastStore, t)
            if (shouldMarkGlobalErrorToastHandled(error, toastStore, noopToastStore)) {
                markGlobalErrorToastHandled(error)
            }
            return Promise.reject(error)
        }

        // If 401 and not already retrying
        if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh) {
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
                    .catch((err) => {
                        return Promise.reject(err)
                    })
            }

            originalRequest._retry = true
            setRefreshInProgress(true)

            try {
                // Use a separate instance or direct call to avoid infinite loop if refresh fails
                const { data } = await axios.post<ApiResponse<RefreshTokenResponse>>(
                    `${api.defaults.baseURL}${API_PATHS.REFRESH}`,
                    undefined,
                    {
                        withCredentials: true,
                    },
                )

                if (data.success) {
                    const refreshedAccessToken = unwrapApiData(data).accessToken
                    resetSessionExpiredToastDebounce()

                    // Update user state (permissions, etc.) with new token
                    const authStore = await resolveAuthStore()
                    const newAccessToken = applyRefreshedAccessToken(authStore, refreshedAccessToken)

                    if (authStore) {
                        // Pass skipAuthRefresh to prevent infinite loop if getMe fails
                        const didFetchUser = await authStore.fetchUser({ skipAuthRefresh: true })
                        if (!didFetchUser) {
                            const hydrationError = new Error('User hydration failed after token refresh') as SuppressibleApiError
                            hydrationError.isUserHydrationFailure = true
                            throw hydrationError
                        }
                    }

                    // Process queued requests
                    processRefreshQueue(null, newAccessToken)

                    // Retry original request with new token
                    if (originalRequest.headers) {
                        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
                    }
                    return api(originalRequest)
                } else {
                    throw new Error('Refresh failed')
                }
            } catch (refreshError) {
                const suppressibleRefreshError = refreshError as SuppressibleApiError
                suppressibleRefreshError.suppressGlobalErrorToast = true
                suppressibleRefreshError.isAuthRefreshFailure = true

                processRefreshQueue(suppressibleRefreshError, null)

                const axiosRefreshError = refreshError as AxiosError
                const refreshStatus = axiosRefreshError.response?.status

                // Check if we are already on the login page to avoid infinite redirect loop
                const isLoginPage = isLoginPathname()

                if ((refreshStatus === 401 || refreshStatus === 403 || !axiosRefreshError.response) && !suppressibleRefreshError.isUserHydrationFailure) {
                    const authStore = await resolveAuthStore()
                    clearExpiredAuthSession(authStore)

                    if (!isLoginPage) {
                        if (router.currentRoute.value.meta.requiresAuth) {
                            notifySessionExpired(toastStore, t('common.messages.sessionExpired'))
                            void router.push({
                                path: API_PATHS.LOGIN,
                                query: { redirect: router.currentRoute.value.fullPath }
                            })
                        }
                    }
                }
                return Promise.reject(suppressibleRefreshError)
            } finally {
                setRefreshInProgress(false)
            }
        }

        // Handle redirect on error
        if (originalRequest?.redirectOnError) {
            const status = error.response?.status || 500
            const errorData = error.response?.data as ApiErrorResponse | undefined
            const message = errorData?.message || error.message
            router.push({ name: 'error', query: { status: status.toString(), message } })
            return Promise.reject(error)
        }

        // Skip global error handler if requested
        if (originalRequest?.skipGlobalErrorHandler) {
            return Promise.reject(error)
        }

        // Handle other common errors
        handleApiError(error, toastStore, t)
        if (shouldMarkGlobalErrorToastHandled(error, toastStore, noopToastStore)) {
            markGlobalErrorToastHandled(error)
        }

        return Promise.reject(error)
    }
)

export default api
