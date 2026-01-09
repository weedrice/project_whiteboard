import { useToastStore } from '@/stores/toast'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { AxiosError } from 'axios'
import { extractValidationErrors, extractErrorMessage, getFieldError, combineValidationErrors } from '@/utils/errorHandler'
import type { ValidationErrors } from '@/types/common'

/**
 * 에러 처리를 위한 Composable
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
     * 에러를 처리하고 사용자에게 알림을 표시합니다.
     * @param error 에러 객체 (AxiosError 또는 일반 Error)
     * @param defaultMessage 기본 에러 메시지 (에러에서 메시지를 추출할 수 없는 경우)
     * @param logError 에러를 로그에 기록할지 여부 (기본값: true)
     */
    const handleError = (error: unknown, defaultMessage?: string, logError: boolean = true) => {
        if (logError) {
            logger.error('Error occurred:', error)
        }

        const axiosError = error as AxiosError
        const message = extractErrorMessage(axiosError) || defaultMessage || t('common.error.unknown')

        toastStore.addToast(message, 'error')
    }

    /**
     * 에러를 조용히 처리합니다 (토스트 알림 없이 로그만 기록).
     * 중요하지 않은 작업 실패 시 사용합니다.
     * @param error 에러 객체
     * @param context 에러 컨텍스트 (로그에 포함될 메시지)
     */
    const handleSilentError = (error: unknown, context?: string) => {
        const contextMessage = context ? `${context}: ` : ''
        logger.warn(`${contextMessage}Error occurred (silent)`, error)
    }

    /**
     * Validation 에러를 처리하고 필드별 에러를 반환합니다.
     * @param error 에러 객체 (AxiosError)
     * @returns 필드별 Validation 에러 객체
     */
    const handleValidationError = (error: unknown): ValidationErrors | null => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)

        if (validationErrors) {
            // 첫 번째 필드의 첫 번째 에러만 토스트로 표시
            const firstField = Object.keys(validationErrors)[0]
            const firstError = firstField ? getFieldError(validationErrors, firstField) : null
            
            if (firstError) {
                toastStore.addToast(firstError, 'error')
            }
        } else {
            // Validation 에러가 아니면 일반 에러 처리
            handleError(error)
        }

        return validationErrors
    }

    /**
     * 특정 필드의 Validation 에러 메시지를 가져옵니다.
     * @param error 에러 객체 (AxiosError)
     * @param fieldName 필드명
     * @returns 필드의 에러 메시지 또는 null
     */
    const getFieldErrorMessage = (error: unknown, fieldName: string): string | null => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)
        return getFieldError(validationErrors, fieldName)
    }

    /**
     * 모든 Validation 에러를 하나의 메시지로 합쳐서 반환합니다.
     * @param error 에러 객체 (AxiosError)
     * @param separator 구분자 (기본값: ', ')
     * @returns 합쳐진 에러 메시지
     */
    const getCombinedValidationErrors = (error: unknown, separator: string = ', '): string => {
        const axiosError = error as AxiosError
        const validationErrors = extractValidationErrors(axiosError)
        return combineValidationErrors(validationErrors, separator)
    }

    /**
     * 네트워크 에러인지 확인합니다.
     * @param error 에러 객체
     * @returns 네트워크 에러 여부
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
     * 재시도 가능한 에러인지 확인합니다.
     * @param error 에러 객체
     * @returns 재시도 가능 여부
     */
    const isRetryableError = (error: unknown): boolean => {
        const axiosError = error as AxiosError
        
        // 네트워크 에러는 재시도 가능
        if (isNetworkError(error)) {
            return true
        }

        // 5xx 서버 에러는 재시도 가능
        const status = axiosError.response?.status
        if (status && status >= 500 && status < 600) {
            return true
        }

        // 429 Too Many Requests는 재시도 가능 (Rate Limiting)
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
