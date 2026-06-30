import { authApi } from '@/api/auth'
import type { User } from '@/types'
import {
    apiEnvelopeResponse,
    apiSuccessDataResponse,
    apiSuccessResponse,
} from '@/test/apiResponseFixtures'

export const authUser = (overrides: Partial<User> = {}): User => ({
    userId: 1,
    loginId: 'test',
    displayName: 'Test User',
    email: 'test@example.com',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-05-27T00:00:00Z',
    ...overrides,
})

export const authLoginResponse = (
    user: User,
    accessToken = 'new-token',
) => apiSuccessDataResponse<typeof authApi.login>({
    accessToken,
    expiresIn: 1800,
    user,
})

export const authLoginFailureResponse = (user: User, accessToken = 'ignored-token') =>
    apiEnvelopeResponse<typeof authApi.login>(false, {
        accessToken,
        expiresIn: 1800,
        user,
    })

export const authUserResponse = (user: User) =>
    apiSuccessDataResponse<typeof authApi.getMe>(user)

export const authUserFailureResponse = () =>
    apiEnvelopeResponse<typeof authApi.getMe>(false, null)

export const authLogoutResponse = () =>
    apiSuccessResponse<typeof authApi.logout>()
