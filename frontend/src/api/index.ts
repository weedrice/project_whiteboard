import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'

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

import type { ErrorResponse, ValidationErrors } from '@/types/common'

interface ApiErrorResponse {
    success?: boolean
    error?: ErrorResponse
    status?: number
    code?: string
    message?: string
    data?: any
}

interface FailedRequest {
    resolve: (token: string | null) => void
    reject: (error: any) => void
}

const api: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_URL || '/api/v1',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
})

// Request Interceptor
api.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const token = localStorage.getItem('accessToken')
        // Skip adding token for auth endpoints to avoid 401s with expired tokens on public endpoints
        const isAuthEndpoint = config.url?.includes('/auth/')

        if (token && !isAuthEndpoint) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error: AxiosError) => {
        return Promise.reject(error)
    }
)

// Concurrency handling for token refresh
let isRefreshing = false
let failedQueue: FailedRequest[] = []

const processQueue = (error: any, token: string | null = null) => {
    failedQueue.forEach((prom) => {
        if (error) {
            prom.reject(error)
        } else {
            prom.resolve(token)
        }
    })
    failedQueue = []
}

// Helper for error handling
const handleApiError = (error: AxiosError, toastStore: any) => {
    if (error.response) {
        const status = error.response.status
        const errorData = error.response.data as ApiErrorResponse | undefined
        
        // ApiResponse 형태의 에러 응답 처리
        const apiError = errorData?.error || errorData
        const message = apiError?.message || errorData?.message || error.message

        switch (status) {
            case 400:
                // Validation 에러인 경우 details가 있을 수 있음
                // Validation 에러는 필드별로 표시되므로 여기서는 요약 메시지만 표시
                if (apiError?.details && typeof apiError.details === 'object') {
                    // Validation 에러의 경우 첫 번째 필드의 첫 번째 에러만 토스트로 표시
                    const validationErrors = apiError.details as ValidationErrors
                    const firstField = Object.keys(validationErrors)[0]
                    const firstError = firstField ? validationErrors[firstField]?.[0] : null
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
                toastStore.addToast(message || t('common.messages.serverError'), 'error', 3000, 'top-center')
                break
            default:
                if (status !== 401) {
                    toastStore.addToast(message || t('common.messages.unknown'), 'error', 3000, 'top-center')
                }
        }
    } else if (error.request) {
        // Network error - 재시도 가능한 오류인지 확인
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
            toastStore.addToast(error.message || t('common.messages.network'), 'error', 3000, 'top-center')
        }
    } else {
        toastStore.addToast(error.message || t('common.messages.requestSetup'), 'error', 3000, 'top-center')
    }
}

// Response Interceptor
api.interceptors.response.use(
    (response: AxiosResponse) => {
        return response
    },
    async (error: AxiosError) => {
        const originalRequest = error.config as InternalAxiosRequestConfig
        // Import store dynamically to avoid circular dependency issues during initialization
        const { useToastStore } = await import('@/stores/toast')
        const toastStore = useToastStore()

        // If 401 and not already retrying
        if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh) {
            if (isRefreshing) {
                return new Promise<string | null>((resolve, reject) => {
                    failedQueue.push({ resolve, reject })
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
            isRefreshing = true

            try {
                const refreshToken = localStorage.getItem('refreshToken')
                if (!refreshToken) {
                    // No refresh token, cannot refresh.
                    localStorage.removeItem('accessToken')
                    throw new Error('No refresh token')
                }

                // Use a separate instance or direct call to avoid infinite loop if refresh fails
                const { data } = await axios.post(`${api.defaults.baseURL}${API_PATHS.REFRESH}`, { refreshToken })

                if (data.success) {
                    const newAccessToken = data.data.accessToken
                    localStorage.setItem('accessToken', newAccessToken)

                    // Update user state (permissions, etc.) with new token
                    const { useAuthStore } = await import('@/stores/auth')
                    const authStore = useAuthStore()
                    authStore.accessToken = newAccessToken

                    // Pass skipAuthRefresh to prevent infinite loop if getMe fails
                    await authStore.fetchUser({ skipAuthRefresh: true })

                    // Process queued requests
                    processQueue(null, newAccessToken)

                    // Retry original request with new token
                    if (originalRequest.headers) {
                        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
                    }
                    return api(originalRequest)
                } else {
                    throw new Error('Refresh failed')
                }
            } catch (refreshError) {
                processQueue(refreshError, null)

                const axiosRefreshError = refreshError as AxiosError
                const refreshStatus = axiosRefreshError.response?.status

                // Check if we are already on the login page to avoid infinite redirect loop
                const isLoginPage = window.location.pathname === API_PATHS.LOGIN

                if (refreshStatus === 401 || refreshStatus === 403 || !axiosRefreshError.response) {
                    if (!localStorage.getItem('refreshToken') || refreshStatus === 401 || refreshStatus === 403) {
                        const hadToken = !!localStorage.getItem('accessToken') || !!localStorage.getItem('refreshToken')

                        localStorage.removeItem('accessToken')
                        localStorage.removeItem('refreshToken')

                        // Update auth store state
                        const { useAuthStore } = await import('@/stores/auth')
                        const authStore = useAuthStore()
                        authStore.user = null
                        authStore.accessToken = ''

                        if (!isLoginPage) {
                            // Only redirect if the current route requires authentication
                            if (router.currentRoute.value.meta.requiresAuth) {
                                toastStore.addToast(t('common.messages.sessionExpired'), 'warning')
                                window.location.href = `${API_PATHS.LOGIN}?redirect=` + encodeURIComponent(window.location.pathname)
                            } else if (hadToken) {
                                // Only reload if we actually had a token (meaning we just logged out)
                                window.location.reload()
                            }
                        }
                    }
                }
                return Promise.reject(refreshError)
            } finally {
                isRefreshing = false
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

        return Promise.reject(error)
    }
)

export default api
