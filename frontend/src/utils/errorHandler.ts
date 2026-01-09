import type { AxiosError } from 'axios'
import type { ErrorResponse, ValidationErrors } from '@/types/common'

interface ApiErrorResponse {
    success?: boolean
    error?: ErrorResponse
    status?: number
    code?: string
    message?: string
    data?: any
}

/**
 * Axios 에러에서 Validation 에러를 추출합니다.
 * @param error Axios 에러 객체
 * @returns Validation 에러 객체 (필드명 -> 에러 메시지 배열) 또는 null
 */
export function extractValidationErrors(error: AxiosError): ValidationErrors | null {
    if (!error.response) {
        return null
    }

    const errorData = error.response.data as ApiErrorResponse | undefined
    const apiError = errorData?.error || errorData

    // Validation 에러인지 확인 (details가 있고 객체 형태인 경우)
    if (apiError?.details && typeof apiError.details === 'object' && !Array.isArray(apiError.details)) {
        return apiError.details as ValidationErrors
    }

    return null
}

/**
 * Axios 에러에서 에러 메시지를 추출합니다.
 * @param error Axios 에러 객체
 * @returns 에러 메시지
 */
export function extractErrorMessage(error: AxiosError): string {
    if (!error.response) {
        return error.message || 'An error occurred'
    }

    const errorData = error.response.data as ApiErrorResponse | undefined
    const apiError = errorData?.error || errorData

    return apiError?.message || errorData?.message || error.message || 'An error occurred'
}

/**
 * Axios 에러에서 ErrorResponse를 추출합니다.
 * @param error Axios 에러 객체
 * @returns ErrorResponse 또는 null
 */
export function extractErrorResponse(error: AxiosError): ErrorResponse | null {
    if (!error.response) {
        return null
    }

    const errorData = error.response.data as ApiErrorResponse | undefined
    const apiError = errorData?.error || errorData

    if (apiError?.code && apiError?.message) {
        return {
            code: apiError.code,
            message: apiError.message,
            details: apiError.details
        }
    }

    return null
}

/**
 * 특정 필드의 Validation 에러 메시지를 가져옵니다.
 * @param validationErrors Validation 에러 객체
 * @param fieldName 필드명
 * @returns 첫 번째 에러 메시지 또는 null
 */
export function getFieldError(validationErrors: ValidationErrors | null, fieldName: string): string | null {
    if (!validationErrors || !validationErrors[fieldName]) {
        return null
    }

    const errors = validationErrors[fieldName]
    return errors && errors.length > 0 ? errors[0] : null
}

/**
 * 모든 Validation 에러를 하나의 메시지로 합칩니다.
 * @param validationErrors Validation 에러 객체
 * @param separator 구분자 (기본값: ', ')
 * @returns 합쳐진 에러 메시지
 */
export function combineValidationErrors(validationErrors: ValidationErrors | null, separator: string = ', '): string {
    if (!validationErrors) {
        return ''
    }

    const allErrors: string[] = []
    Object.values(validationErrors).forEach(errors => {
        allErrors.push(...errors)
    })

    return allErrors.join(separator)
}
