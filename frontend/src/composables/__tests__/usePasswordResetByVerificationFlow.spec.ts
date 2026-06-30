import { describe, expect, it, vi, beforeEach } from 'vitest'
import { usePasswordResetByVerificationFlow } from '../usePasswordResetByVerificationFlow'
import { authApi } from '@/api/auth'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'

const routerPush = vi.hoisted(() => vi.fn())
const toastMock = vi.hoisted(() => ({
    addToast: vi.fn()
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: routerPush,
    }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
    const actual = await importOriginal<typeof import('vue-i18n')>()
    return {
        ...actual,
        useI18n: () => ({
            t: (key: string) => key,
        }),
    }
})

vi.mock('@/api/auth', () => ({
    authApi: {
        resetPassword: vi.fn(),
    },
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => toastMock,
}))

describe('usePasswordResetByVerificationFlow', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('marks password reset verification complete without changing the page state shape', () => {
        const onVerified = vi.fn()
        const { completeVerification } = usePasswordResetByVerificationFlow({
            getEmail: () => 'user@example.com',
            getVerificationTicket: () => '',
            getNewPassword: () => '',
            getConfirmPassword: () => '',
            onVerified
        })

        completeVerification('ticket-1')

        expect(onVerified).toHaveBeenCalledWith('ticket-1')
        expect(toastMock.addToast).toHaveBeenCalledWith('auth.codeVerified', 'success')
    })

    it('does not call reset password API when password validation fails', async () => {
        const { resetPassword } = usePasswordResetByVerificationFlow({
            getEmail: () => 'user@example.com',
            getVerificationTicket: () => 'ticket-1',
            getNewPassword: () => 'weak',
            getConfirmPassword: () => 'weak',
        })

        await resetPassword()

        expect(authApi.resetPassword).not.toHaveBeenCalled()
        expect(toastMock.addToast).toHaveBeenCalledWith('auth.validation.passwordStrength', 'error')
    })

    it('resets password with the existing request payload and redirects to login', async () => {
        vi.mocked(authApi.resetPassword).mockResolvedValue(apiSuccessResponse<typeof authApi.resetPassword>())
        const onLoadingChange = vi.fn()
        const { resetPassword } = usePasswordResetByVerificationFlow({
            getEmail: () => ' user@example.com ',
            getVerificationTicket: () => 'ticket-1',
            getNewPassword: () => 'Password1!',
            getConfirmPassword: () => 'Password1!',
            onLoadingChange
        })

        await resetPassword()

        expect(authApi.resetPassword).toHaveBeenCalledWith({
            email: 'user@example.com',
            verificationTicket: 'ticket-1',
            newPassword: 'Password1!'
        })
        expect(toastMock.addToast).toHaveBeenCalledWith('auth.passwordResetSuccess', 'success')
        expect(routerPush).toHaveBeenCalledWith('/login')
        expect(onLoadingChange).toHaveBeenNthCalledWith(1, true)
        expect(onLoadingChange).toHaveBeenLastCalledWith(false)
    })

    it('redirects deleted users to signup with the existing encoded email query', async () => {
        vi.mocked(authApi.resetPassword).mockRejectedValue({
            isAxiosError: true,
            response: {
                data: {
                    error: {
                        code: 'A009'
                    }
                }
            }
        })
        const { resetPassword } = usePasswordResetByVerificationFlow({
            getEmail: () => 'deleted+user@example.com',
            getVerificationTicket: () => 'ticket-1',
            getNewPassword: () => 'Password1!',
            getConfirmPassword: () => 'Password1!',
        })

        await resetPassword()

        expect(toastMock.addToast).toHaveBeenCalledWith('auth.userDeleted', 'info')
        expect(routerPush).toHaveBeenCalledWith('/signup?email=deleted%2Buser%40example.com')
    })
})
