import { QueryCache, QueryClient, MutationCache } from '@tanstack/vue-query'
import type { AxiosError } from 'axios'
import logger from '@/utils/logger'
import { QUERY_STALE_TIME } from '@/utils/constants'

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

    const axiosError = error as Error & { response?: { data?: { message?: string } } }
    const message = axiosError.response?.data?.message || error.message || 'An error occurred'
    toastStore.addToast(message, 'error')
}

function shouldSuppressGlobalErrorToast(error: unknown): boolean {
    const suppressible = error as {
        suppressGlobalErrorToast?: boolean
        isAuthRefreshFailure?: boolean
        response?: { status?: number }
        config?: { url?: string }
    }

    if (suppressible?.suppressGlobalErrorToast || suppressible?.isAuthRefreshFailure) {
        return true
    }

    const status = suppressible?.response?.status
    const url = suppressible?.config?.url
    return status === 401 && typeof url === 'string' && url.includes('/auth/refresh')
}

export const queryClient = new QueryClient({
    queryCache: new QueryCache({
        onError: (error: Error, query) => {
            if (query.meta?.errorMessage === false) return
            if (shouldSuppressGlobalErrorToast(error)) return

            addErrorToast(error)
            logger.error('Query Error:', error)
        }
    }),
    mutationCache: new MutationCache({
        onError: (error: Error, _variables, _context, mutation) => {
            if (mutation.meta?.errorMessage === false) return
            if (shouldSuppressGlobalErrorToast(error)) return

            addErrorToast(error)
            logger.error('Mutation Error:', error)
        }
    }),
    defaultOptions: {
        queries: {
            staleTime: 0,
            gcTime: QUERY_STALE_TIME.SHORT,
            placeholderData: (previousData: unknown) => previousData,
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
                    return failureCount < 3
                }
                return false
            },
            retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
            refetchOnWindowFocus: false,
            refetchOnReconnect: true
        },
        mutations: {
            retry: false
        }
    }
})
