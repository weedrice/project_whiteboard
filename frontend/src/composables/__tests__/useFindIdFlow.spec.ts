import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useFindIdFlow } from '../useFindIdFlow'
import { authApi } from '@/api/auth'

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
        findId: vi.fn(),
    },
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => toastMock,
}))

describe('useFindIdFlow', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('finds login id with the existing email and verification ticket payload', async () => {
        vi.mocked(authApi.findId).mockResolvedValue({
            data: {
                success: true,
                data: {
                    loginId: 'noviis-user'
                }
            }
        } as never)
        const onSuccess = vi.fn()
        const onLoadingChange = vi.fn()
        const { findId } = useFindIdFlow({
            getEmail: () => ' user@example.com ',
            onLoadingChange,
            onSuccess
        })

        await findId('ticket-1')

        expect(authApi.findId).toHaveBeenCalledWith('user@example.com', 'ticket-1')
        expect(onSuccess).toHaveBeenCalledWith({
            loginId: 'noviis-user',
            verificationTicket: 'ticket-1'
        })
        expect(toastMock.addToast).toHaveBeenCalledWith('auth.codeVerified', 'success')
        expect(onLoadingChange).toHaveBeenNthCalledWith(1, true)
        expect(onLoadingChange).toHaveBeenLastCalledWith(false)
    })

    it('redirects deleted users to signup with the existing encoded email query', async () => {
        vi.mocked(authApi.findId).mockRejectedValue({
            isAxiosError: true,
            response: {
                data: {
                    error: {
                        code: 'A009'
                    }
                }
            }
        })
        const { findId } = useFindIdFlow({
            getEmail: () => 'deleted+user@example.com',
            onSuccess: vi.fn()
        })

        await findId('ticket-1')

        expect(toastMock.addToast).toHaveBeenCalledWith('auth.userDeleted', 'info')
        expect(routerPush).toHaveBeenCalledWith('/signup?email=deleted%2Buser%40example.com')
    })
})
