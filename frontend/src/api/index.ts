import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import router from '@/router'

const { t } = i18n.global

// Extend InternalAxiosRequestConfig to include _retry property
declare module 'axios' {
    export interface AxiosRequestConfig {
        skipGlobalErrorHandler?: boolean;
    }
    export interface InternalAxiosRequestConfig {
        _retry?: boolean;
        redirectOnError?: boolean;
        skipGlobalErrorHandler?: boolean;
    }
}

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
    _retry?: boolean;
    redirectOnError?: boolean;
    skipGlobalErrorHandler?: boolean;
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
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error: any) => {
        return Promise.reject(error)
    }
)

// Response Interceptor
api.interceptors.response.use(
    (response: AxiosResponse) => {
        return response
    },
    async (error: any) => {
        const originalRequest = error.config as CustomAxiosRequestConfig
        // Import store dynamically to avoid circular dependency issues during initialization
        const { useToastStore } = await import('@/stores/toast')
        const toastStore = useToastStore()

        // If 401 and not already retrying
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true

            try {
                const refreshToken = localStorage.getItem('refreshToken')
                if (!refreshToken) {
                    throw new Error('No refresh token')
                }

                // Use a separate instance or direct call to avoid infinite loop if refresh fails
                // Here we assume the backend endpoint is /auth/refresh
                const { data } = await axios.post(`${api.defaults.baseURL}/auth/refresh`, { refreshToken })

                if (data.success) {
                    const newAccessToken = data.data.accessToken
                    localStorage.setItem('accessToken', newAccessToken)

                    // Update user state (permissions, etc.) with new token
                    const { useAuthStore } = await import('@/stores/auth')
                    const authStore = useAuthStore()
                    authStore.accessToken = newAccessToken
                    await authStore.fetchUser()

                    // Retry original request with new token
                    originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
                    return api(originalRequest)
                }
            } catch (refreshError: any) {
                // refresh token이 유효하지 않거나(401/403) refresh API 자체가 실패한 경우에만 로그아웃
                // 네트워크 에러 등 일시적 오류는 로그아웃하지 않음
                const refreshStatus = refreshError.response?.status
                if (refreshStatus === 401 || refreshStatus === 403 || !refreshError.response) {
                    // 401/403: refresh token도 만료됨 → 로그아웃
                    // !refreshError.response + refreshToken 만료: 실제 인증 문제일 가능성 높음
                    // 하지만 네트워크 에러(!refreshError.response)는 구분이 어려우므로
                    // refresh token 자체가 없는 경우에만 확실히 로그아웃
                    if (!localStorage.getItem('refreshToken') || refreshStatus === 401 || refreshStatus === 403) {
                        localStorage.removeItem('accessToken')
                        localStorage.removeItem('refreshToken')
                        toastStore.addToast(t('common.messages.sessionExpired'), 'warning')
                        window.location.href = '/login'
                    }
                }
                return Promise.reject(refreshError)
            }
        }

        // Handle redirect on error
        if (originalRequest?.redirectOnError) {
            const status = error.response?.status || 500
            const message = error.response?.data?.message || error.message
            router.push({ name: 'error', query: { status: status.toString(), message } })
            return Promise.reject(error)
        }

        // Skip global error handler if requested
        if (originalRequest?.skipGlobalErrorHandler) {
            return Promise.reject(error)
        }

        // Handle other common errors
        if (error.response) {
            const status = error.response.status
            const message = error.response.data?.message || error.message

            switch (status) {
                case 400:
                    toastStore.addToast(message || t('common.messages.badRequest'), 'error', 3000, 'top-center')
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
            // Network error
            toastStore.addToast(error.message || t('common.messages.network'), 'error', 3000, 'top-center')
        } else {
            toastStore.addToast(error.message || t('common.messages.requestSetup'), 'error', 3000, 'top-center')
        }

        return Promise.reject(error)
    }
)

export default api
