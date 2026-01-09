import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'

import { VueQueryPlugin, QueryClient, QueryCache, MutationCache } from '@tanstack/vue-query'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import type { AxiosError } from 'axios'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)

import { QUERY_STALE_TIME } from '@/utils/constants'

const queryClient = new QueryClient({
    queryCache: new QueryCache({
        onError: (error: Error, query) => {
            if (query.meta?.errorMessage === false) return

            const toastStore = useToastStore()
            const axiosError = error as Error & { response?: { data?: { message?: string } } }
            const message = axiosError.response?.data?.message || error.message || 'An error occurred'
            toastStore.addToast(message, 'error')
            logger.error('Query Error:', error)
        }
    }),
    mutationCache: new MutationCache({
        onError: (error: Error, variables, context, mutation) => {
            if (mutation.meta?.errorMessage === false) return

            const toastStore = useToastStore()
            const axiosError = error as Error & { response?: { data?: { message?: string } } }
            const message = axiosError.response?.data?.message || error.message || 'An error occurred'
            toastStore.addToast(message, 'error')
            logger.error('Mutation Error:', error)
        }
    }),
    defaultOptions: {
        queries: {
            // 기본 staleTime: 0 (항상 fresh로 간주)
            // 개별 쿼리에서 필요에 따라 설정
            staleTime: 0,
            // 기본 gcTime: 5분 (이전 cacheTime)
            gcTime: QUERY_STALE_TIME.SHORT,
            // placeholderData로 이전 데이터 유지
            placeholderData: (previousData) => previousData,
            retry: (failureCount, error: unknown) => {
                // 네트워크 오류나 5xx 서버 오류인 경우에만 재시도
                const axiosError = error as AxiosError
                if (!axiosError.response) {
                    // 네트워크 오류
                    return failureCount < 2 // 최대 2번 재시도
                }
                const status = axiosError.response?.status
                if (status && status >= 500 && status < 600) {
                    // 5xx 서버 오류
                    return failureCount < 2
                }
                if (status === 429) {
                    // Rate limiting
                    return failureCount < 3
                }
                return false
            },
            retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000), // 지수 백오프, 최대 30초
            refetchOnWindowFocus: false,
            refetchOnReconnect: true // 네트워크 재연결 시 자동 재요청
        },
        mutations: {
            retry: false // Mutation은 기본적으로 재시도하지 않음
        }
    }
})

app.use(VueQueryPlugin, { queryClient })

// Global Error Handler
app.config.errorHandler = (err, instance, info) => {
    logger.error('Global Error Handler:', err)
    logger.error('Vue Instance:', instance)
    logger.error('Error Info:', info)
}

app.mount('#app')

// Web Vitals 성능 모니터링 (프로덕션 환경에서만)
if (import.meta.env.PROD) {
    reportWebVitals(logMetric)
}
