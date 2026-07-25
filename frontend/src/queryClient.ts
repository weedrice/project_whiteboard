import { QueryCache, QueryClient, MutationCache } from '@tanstack/vue-query'
import type { AxiosError } from 'axios'
import logger from '@/utils/logger'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { extractErrorMessage, shouldSuppressGlobalErrorToast } from '@/utils/errorHandler'
import { createErrorLogPayload } from '@/utils/vueErrorLog'
import { isStaleMutationSessionContext } from '@/queryAuthScope'
import { getRetryAfterMs, shouldRetryAfterDelay } from '@/api/retryAfter'

interface ToastStoreLike {
    addToast: (message: string, type: 'error') => void
}

let resolveToastStore: (() => ToastStoreLike) | null = null

export function configureQueryClientStoreResolvers(resolvers: {
    resolveToastStore: () => ToastStoreLike
}) {
    resolveToastStore = resolvers.resolveToastStore
}

function addErrorToast(error: Error) {
    const toastStore = resolveToastStore?.()
    if (!toastStore) return

    toastStore.addToast(extractErrorMessage(error), 'error')
}

export const queryClient = new QueryClient({
    queryCache: new QueryCache({
        onError: (error: Error, query) => {
            if (query.meta?.errorMessage === false) return
            if (shouldSuppressGlobalErrorToast(error)) return

            addErrorToast(error)
            logger.error('Query Error:', createErrorLogPayload(error))
        }
    }),
    mutationCache: new MutationCache({
        onError: (error: Error, _variables, context, mutation) => {
            if (isStaleMutationSessionContext(context)) return
            if (mutation.meta?.errorMessage === false) return
            if (shouldSuppressGlobalErrorToast(error)) return

            addErrorToast(error)
            logger.error('Mutation Error:', createErrorLogPayload(error))
        }
    }),
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            gcTime: QUERY_STALE_TIME.SHORT,
            retry: (failureCount, error: unknown) => {
                const axiosError = error as AxiosError
                if (!axiosError.response) {
                    return failureCount < 2
                }
                const status = axiosError.response?.status
                if (status && status >= 500 && status < 600) {
                    return failureCount < 2
                }
                if (status === 429) {
                    const retryAfterMs = getRetryAfterMs(axiosError)
                    // 서버가 대기 시간을 알려줬다면 그만큼만 기다렸다가 한 번만 다시 시도한다.
                    // 지시가 상한을 넘으면 화면을 멈춰 두지 않고 즉시 오류를 노출한다.
                    if (retryAfterMs !== null) {
                        return shouldRetryAfterDelay(retryAfterMs) && failureCount < 1
                    }
                    return failureCount < 3
                }
                return false
            },
            // 429에서는 서버가 알려준 Retry-After를 우선 따르고, 없을 때만 지수 백오프로 떨어진다.
            retryDelay: (attemptIndex, error: unknown) =>
                getRetryAfterMs(error) ?? Math.min(1000 * 2 ** attemptIndex, 30000),
            refetchOnWindowFocus: false,
            refetchOnReconnect: true
        },
        mutations: {
            retry: false
        }
    }
})
