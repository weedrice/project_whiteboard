import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosRequestConfig } from 'axios'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { authApi } from '../auth'

describe('authApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls login and signup with skipGlobalErrorHandler', () => {
        const credentials = { loginId: 'tester', password: 'secret' }
        const signupData = { loginId: 'tester', password: 'secret', email: 'a@b.com', displayName: 'tester' }

        authApi.login(credentials)
        authApi.signup(signupData)

        expect(apiMock.post).toHaveBeenNthCalledWith(
            1,
            '/auth/login',
            credentials,
            { skipGlobalErrorHandler: true },
        )
        expect(apiMock.post).toHaveBeenNthCalledWith(
            2,
            '/auth/signup',
            signupData,
            { skipGlobalErrorHandler: true },
        )
    })

    it('calls logout and refreshToken endpoints', () => {
        authApi.logout('refresh-token')
        authApi.refreshToken('refresh-token')

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/auth/logout', { refreshToken: 'refresh-token' })
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/auth/refresh', { refreshToken: 'refresh-token' })
    })

    it('passes optional config to getMe', () => {
        const config = { skipAuthRefresh: true } as AxiosRequestConfig
        authApi.getMe(config)

        expect(apiMock.get).toHaveBeenCalledWith('/users/me', config)
    })

    it('uses default false for sendVerificationCode forSignup flag', () => {
        authApi.sendVerificationCode('test@example.com')

        expect(apiMock.post).toHaveBeenCalledWith(
            '/auth/email/send-verification',
            { email: 'test@example.com', forSignup: false },
            { skipAuthRefresh: true },
        )
    })

    it('supports true for sendVerificationCode forSignup flag', () => {
        authApi.sendVerificationCode('test@example.com', true)

        expect(apiMock.post).toHaveBeenCalledWith(
            '/auth/email/send-verification',
            { email: 'test@example.com', forSignup: true },
            { skipAuthRefresh: true },
        )
    })

    it('calls verifyCode with required flags', () => {
        authApi.verifyCode('test@example.com', '123456')

        expect(apiMock.post).toHaveBeenCalledWith(
            '/auth/email/verify',
            { email: 'test@example.com', code: '123456' },
            {
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        )
    })

    it('calls reregister check with params and auth refresh skip', () => {
        authApi.checkEmailForReregister('test@example.com')

        expect(apiMock.get).toHaveBeenCalledWith(
            '/auth/reregister/check-email',
            {
                params: { email: 'test@example.com' },
                skipAuthRefresh: true,
            },
        )
    })

    it('calls findId with skip flags', () => {
        authApi.findId('test@example.com')

        expect(apiMock.post).toHaveBeenCalledWith(
            '/auth/find-id',
            { email: 'test@example.com' },
            {
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        )
    })

    it('calls password reset APIs with proper payload and flags', () => {
        const resetData = {
            email: 'test@example.com',
            code: '111111',
            newPassword: 'new-password',
        }

        authApi.sendPasswordReset('test@example.com')
        authApi.resetPassword(resetData)
        authApi.sendPasswordResetLinkByEmail('test@example.com')
        authApi.resetPasswordWithToken('token-1', 'new-password')

        expect(apiMock.post).toHaveBeenNthCalledWith(
            1,
            '/auth/password/send-reset-link',
            { email: 'test@example.com' },
            { skipAuthRefresh: true },
        )
        expect(apiMock.post).toHaveBeenNthCalledWith(
            2,
            '/auth/password/reset-by-code',
            resetData,
            { skipAuthRefresh: true, skipGlobalErrorHandler: true },
        )
        expect(apiMock.post).toHaveBeenNthCalledWith(
            3,
            '/auth/password/send-reset-link-by-email',
            { email: 'test@example.com' },
            { skipAuthRefresh: true, skipGlobalErrorHandler: true },
        )
        expect(apiMock.post).toHaveBeenNthCalledWith(
            4,
            '/auth/password/reset',
            { token: 'token-1', newPassword: 'new-password' },
            { skipAuthRefresh: true },
        )
    })
})
