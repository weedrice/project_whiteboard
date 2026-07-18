import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useFindIdFlow } from '../useFindIdFlow'
import { authApi } from '@/api/auth'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'

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
        vi.mocked(authApi.findId).mockResolvedValue(
            apiSuccessDataResponse<typeof authApi.findId>({
                loginId: 'noviis-user'
            })
        )
        const onSuccess = vi.fn()
        const onLoadingChange = vi.fn()
        const { findId } = useFindIdFlow({
            getEmail: () => ' user@example.com ',
            onLoadingChange,
            onSuccess
        })

        await findId('ticket-1')

        expect(authApi.findId).toHaveBeenCalledWith(
            'user@example.com',
            'ticket-1',
            expect.objectContaining({ signal: expect.any(AbortSignal) }),
        )
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

    it('aborts and discards a stale lookup when the flow is cancelled', async () => {
        const pending = createDeferred<Awaited<ReturnType<typeof authApi.findId>>>()
        vi.mocked(authApi.findId).mockReturnValueOnce(pending.promise)
        const onSuccess = vi.fn()
        const onLoadingChange = vi.fn()
        const flow = useFindIdFlow({
            getEmail: () => 'user@example.com',
            onLoadingChange,
            onSuccess,
        })

        const lookup = flow.findId('ticket-1')
        const signal = vi.mocked(authApi.findId).mock.calls[0][2]?.signal
        flow.cancelPendingRequests()
        expect(signal?.aborted).toBe(true)

        pending.resolve(apiSuccessDataResponse<typeof authApi.findId>({ loginId: 'stale-user' }))
        await lookup

        expect(onSuccess).not.toHaveBeenCalled()
        expect(toastMock.addToast).not.toHaveBeenCalled()
        expect(onLoadingChange).toHaveBeenLastCalledWith(false)
    })
})
