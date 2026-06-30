import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'
import i18n from '@/i18n'
import {
    combineValidationErrors,
    extractErrorMessage,
    extractErrorResponse,
    extractValidationErrors,
    getFieldError,
    isRestrictedResourceError,
    isValidationErrors,
    normalizeApiErrorMessage,
    shouldSuppressGlobalErrorToast,
} from '@/utils/errorHandler'

describe('errorHandler', () => {
    it('normalizes generic axios status-code messages', () => {
        expect(normalizeApiErrorMessage('Request failed with status code 500')).toBe(i18n.global.t('common.messages.serverError'))
        expect(normalizeApiErrorMessage(undefined)).toBe(i18n.global.t('common.messages.serverError'))
    })

    it('extracts validation errors from axios response payload', () => {
        const error = {
            response: {
                data: {
                    error: {
                        details: {
                            email: ['Invalid email format'],
                            password: ['Too short'],
                        },
                    },
                },
            },
        } as AxiosError

        const validationErrors = extractValidationErrors(error)
        expect(validationErrors).toEqual({
            email: ['Invalid email format'],
            password: ['Too short'],
        })
        expect(getFieldError(validationErrors, 'email')).toBe('Invalid email format')
        expect(combineValidationErrors(validationErrors, ' | ')).toBe('Invalid email format | Too short')
    })

    it('returns null when validation details are missing or invalid', () => {
        const noResponseError = {} as AxiosError
        const invalidDetailsError = {
            response: {
                data: {
                    error: {
                        details: ['not-object'],
                    },
                },
            },
        } as AxiosError
        const rootDetailsError = {
            response: {
                data: {
                    details: { name: ['required'] },
                },
            },
        } as AxiosError

        expect(extractValidationErrors(noResponseError)).toBeNull()
        expect(extractValidationErrors(invalidDetailsError)).toBeNull()
        expect(extractValidationErrors(rootDetailsError)).toEqual({ name: ['required'] })
    })

    it('rejects malformed validation details instead of treating arbitrary objects as field errors', () => {
        const malformedDetailsError = {
            response: {
                data: {
                    error: {
                        details: {
                            title: 'required',
                            count: [1],
                        },
                    },
                },
            },
        } as AxiosError

        expect(isValidationErrors({ title: ['required'] })).toBe(true)
        expect(isValidationErrors({ title: 'required' })).toBe(false)
        expect(isValidationErrors(['required'])).toBe(false)
        expect(extractValidationErrors(malformedDetailsError)).toBeNull()
    })

    it('extracts message and structured error response safely', () => {
        const axiosError = {
            isAxiosError: true,
            message: 'Request failed with status code 400',
            response: {
                data: {
                    error: {
                        code: 'BAD_REQUEST',
                        message: 'Validation failed',
                        details: { field: ['required'] },
                    },
                },
            },
        } as AxiosError

        expect(extractErrorMessage(axiosError)).toBe('Validation failed')
        expect(extractErrorResponse(axiosError)).toEqual({
            code: 'BAD_REQUEST',
            message: 'Validation failed',
            details: { field: ['required'] },
        })
    })

    it('handles axios errors without response and fallback paths', () => {
        const axiosErrorWithoutResponse = {
            isAxiosError: true,
            message: 'Request failed with status code 500',
        } as AxiosError
        const axiosErrorWithoutMessage = {
            isAxiosError: true,
            response: {
                data: {},
            },
            message: '',
        } as AxiosError

        expect(extractErrorMessage(axiosErrorWithoutResponse)).toBe(i18n.global.t('common.messages.serverError'))
        expect(extractErrorMessage(axiosErrorWithoutMessage)).toBe(i18n.global.t('common.messages.serverError'))
    })

    it('detects errors that should skip duplicate global error toasts', () => {
        expect(shouldSuppressGlobalErrorToast({ suppressGlobalErrorToast: true })).toBe(true)
        expect(shouldSuppressGlobalErrorToast({ isAuthRefreshFailure: true })).toBe(true)
        expect(shouldSuppressGlobalErrorToast({
            response: { status: 401 },
            config: { url: '/auth/refresh' },
        })).toBe(true)
        expect(shouldSuppressGlobalErrorToast({
            response: { status: 500 },
            config: { url: '/posts' },
        })).toBe(false)
    })

    it('returns null for incomplete error response and handles empty validation helpers', () => {
        const incompleteError = {
            response: {
                data: {
                    error: {
                        code: 'BAD_REQUEST',
                    },
                },
            },
        } as AxiosError
        const noResponseError = {} as AxiosError

        expect(extractErrorResponse(noResponseError)).toBeNull()
        expect(extractErrorResponse(incompleteError)).toBeNull()
        expect(getFieldError(null, 'email')).toBeNull()
        expect(getFieldError({ email: [] }, 'email')).toBeNull()
        expect(combineValidationErrors(null)).toBe('')
    })

    it('falls back for non-axios unknown errors', () => {
        expect(extractErrorMessage(new Error('boom'))).toBe('boom')
        expect(extractErrorMessage('plain-string')).toBe('plain-string')
        expect(extractErrorMessage({})).toBe(i18n.global.t('common.messages.serverError'))
    })

    it('detects restricted resource errors from status and known messages', () => {
        const forbiddenError = {
            isAxiosError: true,
            response: {
                status: 403,
                data: {
                    message: 'Forbidden'
                },
            },
        } as AxiosError

        const notFoundBoardError = {
            isAxiosError: true,
            response: {
                status: 500,
                data: {
                    message: 'Board Not Found'
                },
            },
            message: 'Request failed with status code 500',
        } as AxiosError

        const genericServerError = {
            isAxiosError: true,
            response: {
                status: 500,
                data: {
                    message: 'Internal Server Error'
                },
            },
        } as AxiosError

        expect(isRestrictedResourceError(forbiddenError)).toBe(true)
        expect(isRestrictedResourceError(notFoundBoardError)).toBe(true)
        expect(isRestrictedResourceError(genericServerError)).toBe(false)
        expect(isRestrictedResourceError(new Error('boom'))).toBe(false)
    })
})
