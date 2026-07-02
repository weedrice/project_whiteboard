import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { AxiosError } from 'axios'
import {
    combineValidationErrors,
    extractErrorMessage,
    extractValidationErrors,
    getFieldError,
    shouldSuppressGlobalErrorToast,
} from '@/utils/errorHandler'
import type { ValidationErrors } from '@/types/common'

/**
 * Shared error handling helpers for composables and components.
 *
 * @example
 * ```typescript
 * const { handleError, handleSilentError, handleValidationError } = useErrorHandler()
 *
 * try {
 *   await someApi()
 * } catch (error) {
 *   handleError(error, 'Failed to load data')
 * }
 * ```
 */
export function useErrorHandler() {
    const toastStore = useToastStore()
    const { t } = useI18n()

    /**
     * Logs an error and shows a user-facing toast unless the error opts out.
     * @param error Error-like value, usually an AxiosError.
     * @param defaultMessage Fallback message when the error has no message.
     * @param logError Whether to write the error to the logger.
     */
    const handleError = (error: unknown, defaultMessage?: string, logError: boolean = true) => {
        if (logError) {
            logger.error('Error occurred:', error)
        }

        if (shouldSuppressGlobalErrorToast(error)) {
            return
        }

        const axiosError = error as AxiosError
        const message = extractErrorMessage(axiosError) || defaultMessage || t('common.error.unknown')

        toastStore.addToast(message, 'error')
    }

    /**
     * Logs a non-blocking error without showing a toast.
     * @param error Error-like value.
     * @param context Optional log context.
     */
    const handleSilentError = (error: unknown, context?: string) => {
        const contextMessage = context ? `${context}: ` : ''
        logger.warn(`${contextMessage}Error occurred (silent)`, error)
    }

    /**
     * Handles validation errors and returns field-level messages.
     * @param error Error-like value, usually an AxiosError.
     * @returns Field-level validation messages or null.
     */
    const handleValidationError = (error: unknown): ValidationErrors | null => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)

        if (validationErrors) {
            // Show only the first field error as a toast.
            const firstField = Object.keys(validationErrors)[0]
            const firstError = firstField ? getFieldError(validationErrors, firstField) : null

            if (firstError) {
                toastStore.addToast(firstError, 'error')
            }
        } else {
            // Fall back to normal error handling.
            handleError(error)
        }

        return validationErrors
    }

    /**
     * Gets a validation error message for a specific field.
     * @param error Error-like value, usually an AxiosError.
     * @param fieldName Field name.
     * @returns Field error message or null.
     */
    const getFieldErrorMessage = (error: unknown, fieldName: string): string | null => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)
        return getFieldError(validationErrors, fieldName)
    }

    /**
     * Combines all validation errors into a single message.
     * @param error Error-like value, usually an AxiosError.
     * @param separator Separator between messages.
     * @returns Combined validation error message.
     */
    const getCombinedValidationErrors = (error: unknown, separator: string = ', '): string => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)
        return combineValidationErrors(validationErrors, separator)
    }

    /**
     * Checks whether an error represents a network failure.
     * @param error Error-like value.
     * @returns Whether the error is network-related.
     */
    const isNetworkError = (error: unknown): boolean => {
        const axiosError = error as AxiosError
        return !axiosError.response && (
            axiosError.code === 'ECONNABORTED' || // Timeout
            axiosError.code === 'ERR_NETWORK' || // Network error
            axiosError.message?.includes('Network Error')
        )
    }

    /**
     * Checks whether an operation can reasonably be retried.
     * @param error Error-like value.
     * @returns Whether the error is retryable.
     */
    const isRetryableError = (error: unknown): boolean => {
        const axiosError = error as AxiosError

        // Network errors are usually retryable.
        if (isNetworkError(error)) {
            return true
        }

        // 5xx server errors are usually retryable.
        const status = axiosError.response?.status
        if (status && status >= 500 && status < 600) {
            return true
        }

        // 429 Too Many Requests can be retried after rate limiting.
        if (status === 429) {
            return true
        }

        return false
    }

    return {
        handleError,
        handleSilentError,
        handleValidationError,
        getFieldErrorMessage,
        getCombinedValidationErrors,
        isNetworkError,
        isRetryableError
    }
}
