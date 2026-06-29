import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'
import { Storage } from '@/utils/storage'
import { API } from '@/utils/constants'
import { isValidationErrors, normalizeApiErrorMessage } from '@/utils/errorHandler'
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

const { t } = i18n.global

// Constants
const API_PATHS = {
    REFRESH: '/auth/refresh',
    LOGIN: '/login'
}

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

import type { ApiResponse, ErrorResponse, ValidationErrors } from '@/types/common'

type RefreshTokenResponse = {
    accessToken: string
}

interface ApiErrorResponse {
    success?: boolean
    error?: ErrorResponse
    status?: number
    code?: string
    message?: string
    data?: unknown
    details?: ValidationErrors | Record<string, unknown>
}

interface SuppressibleApiError extends AxiosError {
    suppressGlobalErrorToast?: boolean
    isAuthRefreshFailure?: boolean
    isUserHydrationFailure?: boolean
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

const handleApiError = (error: AxiosError, toastStore: ToastStore) => {
    if (error.response) {
        const status = error.response.status
        const errorData = error.response.data as ApiErrorResponse | undefined

        // Handle ApiResponse-style error payloads.
        const apiError = errorData?.error || errorData
        const rawMessage = apiError?.message || errorData?.message || error.message
        const message = normalizeApiErrorMessage(rawMessage)

        switch (status) {
            case 400:
                // Validation errors may include field-level details. Show the first field error.
                if (isValidationErrors(apiError?.details)) {
                    const firstField = Object.keys(apiError.details)[0]
                    const firstError = firstField ? apiError.details[firstField]?.[0] : null
                    toastStore.addToast(
                        firstError || message || t('common.messages.badRequest'),
                        'error',
                        3000,
                        'top-center'
                    )
                } else {
                    toastStore.addToast(message || t('common.messages.badRequest'), 'error', 3000, 'top-center')
                }
                break
            case 403:
                toastStore.addToast(message || t('common.messages.forbidden'), 'error', 3000, 'top-center')
                break
            case 404:
                toastStore.addToast(message || t('common.messages.notFound'), 'error', 3000, 'top-center')
                break
            case 500:
            case 502:
            case 503:
            case 504:
                toastStore.addToast(t('common.messages.serverError'), 'error', 3000, 'top-center')
                break
            default:
                if (status !== 401) {
                    toastStore.addToast(message || t('common.messages.unknown'), 'error', 3000, 'top-center')
                }
        }
    } else if (error.request) {
        // Network error: distinguish retryable transport failures from generic request errors.
        const isRetryable = !error.response && (
            error.code === 'ECONNABORTED' || // Timeout
            error.code === 'ERR_NETWORK' || // Network error
            error.message?.includes('Network Error')
        )

        if (isRetryable) {
            toastStore.addToast(
                t('common.messages.networkRetry') || 'Network error. Please check your connection and try again.',
                'error',
                5000,
                'top-center'
            )
        } else {
            toastStore.addToast(normalizeApiErrorMessage(error.message) || t('common.messages.network'), 'error', 3000, 'top-center')
        }
    } else {
        toastStore.addToast(normalizeApiErrorMessage(error.message) || t('common.messages.requestSetup'), 'error', 3000, 'top-center')
    }
}

const markGlobalErrorToastHandled = (error: AxiosError) => {
    const suppressibleError = error as SuppressibleApiError
    suppressibleError.suppressGlobalErrorToast = true
}

const shouldMarkGlobalErrorToastHandled = (error: AxiosError, toastStore: ToastStore) => {
    return error.response?.status !== 401 && toastStore !== noopToastStore
}

const isCanceledRequestError = (error: AxiosError) => {
    return error.code === 'ERR_CANCELED' || error.name === 'CanceledError' || error.name === 'AbortError'
}

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
            handleApiError(error, toastStore)
            if (shouldMarkGlobalErrorToastHandled(error, toastStore)) {
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
                const isLoginPage = window.location.pathname === API_PATHS.LOGIN

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
        handleApiError(error, toastStore)
        if (shouldMarkGlobalErrorToastHandled(error, toastStore)) {
            markGlobalErrorToastHandled(error)
        }

        return Promise.reject(error)
    }
)

export default api
