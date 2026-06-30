import type { AxiosError } from 'axios'
import type { ToastStore } from '@/api/authRefreshSession'
import type { ErrorResponse, ValidationErrors } from '@/types/common'
import { isValidationErrors, normalizeApiErrorMessage } from '@/utils/errorHandler'

export interface ApiErrorResponse {
    success?: boolean
    error?: ErrorResponse
    status?: number
    code?: string
    message?: string
    data?: unknown
    details?: ValidationErrors | Record<string, unknown>
}

export interface SuppressibleApiError extends AxiosError {
    suppressGlobalErrorToast?: boolean
    isAuthRefreshFailure?: boolean
    isUserHydrationFailure?: boolean
}

type Translate = (key: string) => string

export const handleApiError = (error: AxiosError, toastStore: ToastStore, t: Translate) => {
    if (error.response) {
        const status = error.response.status
        const errorData = error.response.data as ApiErrorResponse | undefined

        const apiError = errorData?.error || errorData
        const rawMessage = apiError?.message || errorData?.message || error.message
        const message = normalizeApiErrorMessage(rawMessage)

        switch (status) {
            case 400:
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
        const isRetryable = !error.response && (
            error.code === 'ECONNABORTED'
            || error.code === 'ERR_NETWORK'
            || error.message?.includes('Network Error')
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

export const markGlobalErrorToastHandled = (error: AxiosError) => {
    const suppressibleError = error as SuppressibleApiError
    suppressibleError.suppressGlobalErrorToast = true
}

export const shouldMarkGlobalErrorToastHandled = (
    error: AxiosError,
    toastStore: ToastStore,
    noopToastStore: ToastStore
) => {
    return error.response?.status !== 401 && toastStore !== noopToastStore
}

export const isCanceledRequestError = (error: AxiosError) => {
    return error.code === 'ERR_CANCELED' || error.name === 'CanceledError' || error.name === 'AbortError'
}
