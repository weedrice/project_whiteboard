import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'
import i18n from '@/i18n'
import {
    combineValidationErrors,
    extractErrorMessage,
    extractErrorResponse,
    extractValidationErrors,
    getFieldError,
    normalizeApiErrorMessage,
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

    it('falls back for non-axios unknown errors', () => {
        expect(extractErrorMessage(new Error('boom'))).toBe('boom')
        expect(extractErrorMessage('plain-string')).toBe('plain-string')
        expect(extractErrorMessage({})).toBe(i18n.global.t('common.messages.serverError'))
    })
})
