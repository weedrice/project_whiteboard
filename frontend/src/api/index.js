import axios from 'axios'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || '/api/v1',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
})

// Request Interceptor
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('accessToken')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// Response Interceptor
api.interceptors.response.use(
    (response) => {
        return response
    },
    async (error) => {
        const originalRequest = error.config
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

                    // Retry original request with new token
                    originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
                    return api(originalRequest)
                }
            } catch (refreshError) {
                // Refresh failed - clear tokens and redirect to login
                localStorage.removeItem('accessToken')
                localStorage.removeItem('refreshToken')

                toastStore.addToast('세션이 만료되었습니다. 다시 로그인해주세요.', 'warning')

                window.location.href = '/login'
                return Promise.reject(refreshError)
            }
        }

        // Handle other common errors
        if (error.response) {
            const status = error.response.status
            const message = error.response.data?.message || error.message

            switch (status) {
                case 400:
                    toastStore.addToast(message || '잘못된 요청입니다.', 'error')
                    break
                case 403:
                    toastStore.addToast(message || '권한이 없습니다.', 'error')
                    break
                case 404:
                    toastStore.addToast(message || '요청한 리소스를 찾을 수 없습니다.', 'error')
                    break
                case 500:
                    toastStore.addToast(message || '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.', 'error')
                    break
                default:
                    if (status !== 401) {
                        toastStore.addToast(message || '알 수 없는 오류가 발생했습니다.', 'error')
                    }
            }
        } else if (error.request) {
            // Network error
            toastStore.addToast(message || '서버와 통신할 수 없습니다. 인터넷 연결을 확인해주세요.', 'error')
        } else {
            toastStore.addToast(message || '요청 설정 중 오류가 발생했습니다.', 'error')
        }

        return Promise.reject(error)
    }
)

export default api
