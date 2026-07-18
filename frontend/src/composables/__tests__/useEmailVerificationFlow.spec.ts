import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useEmailVerificationFlow } from '../useEmailVerificationFlow'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))

const authStoreMock = vi.hoisted(() => ({
  fetchUser: vi.fn(),
  user: { isEmailVerified: false }
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/api/auth', () => ({
  authApi: {
    sendVerificationCode: vi.fn(),
    verifyCode: vi.fn()
  }
}))

vi.mock('@/api/user', () => ({
  userApi: {
    verifyEmail: vi.fn()
  }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreMock
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => toastMock
}))

describe('useEmailVerificationFlow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authStoreMock.user.isEmailVerified = false
  })

  it('sends change-email verification code with the existing purpose', async () => {
    vi.mocked(authApi.sendVerificationCode).mockResolvedValue(
      apiSuccessResponse<typeof authApi.sendVerificationCode>()
    )
    const flow = useEmailVerificationFlow({
      getEmail: () => 'me@example.com',
      refreshProfile: vi.fn()
    })

    flow.openVerifyModal()
    await flow.sendVerifyCode()

    expect(authApi.sendVerificationCode).toHaveBeenCalledWith(
      'me@example.com',
      'CHANGE_EMAIL',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(flow.emailVerification.isCodeSent).toBe(true)
  })

  it('verifies email then refreshes dashboard profile and global auth user', async () => {
    vi.mocked(authApi.verifyCode).mockResolvedValue(
      apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'ticket-1' })
    )
    vi.mocked(userApi.verifyEmail).mockResolvedValue(apiSuccessResponse<typeof userApi.verifyEmail>())
    authStoreMock.fetchUser.mockResolvedValue(false)
    const refreshProfile = vi.fn().mockResolvedValue(undefined)
    const flow = useEmailVerificationFlow({
      getEmail: () => 'me@example.com',
      refreshProfile
    })
    flow.openVerifyModal()
    flow.emailVerification.code = '123456'
    flow.emailVerification.timeLeft = 60

    await flow.verifyEmailCode()

    expect(authApi.verifyCode).toHaveBeenCalledWith(
      'me@example.com',
      '123456',
      'CHANGE_EMAIL',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(userApi.verifyEmail).toHaveBeenCalledWith({
      email: 'me@example.com',
      verificationTicket: 'ticket-1'
    }, expect.objectContaining({
      signal: expect.any(AbortSignal),
      skipGlobalErrorHandler: true,
    }))
    expect(refreshProfile).toHaveBeenCalled()
    expect(authStoreMock.fetchUser).toHaveBeenCalled()
    expect(authStoreMock.user.isEmailVerified).toBe(true)
    expect(authStoreMock.fetchUser.mock.invocationCallOrder[0])
      .toBeLessThan(refreshProfile.mock.invocationCallOrder[0])
    expect(flow.isVerifyModalOpen.value).toBe(false)
  })

  it('keeps the verification purpose captured when the request starts', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof authApi.verifyCode>>>()
    vi.mocked(authApi.verifyCode).mockReturnValueOnce(deferred.promise)
    let purpose: 'FIND_ID' | 'PASSWORD_RESET' = 'FIND_ID'
    const afterVerify = vi.fn()
    const flow = useEmailVerificationFlow({
      getEmail: () => 'me@example.com',
      getCode: () => '123456',
      purpose: () => purpose,
      useTimer: false,
      afterVerify,
    })

    const verify = flow.verifyEmailCode()
    purpose = 'PASSWORD_RESET'
    deferred.resolve(apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'ticket' }))
    await verify

    expect(authApi.verifyCode).toHaveBeenCalledWith(
      'me@example.com',
      '123456',
      'FIND_ID',
      expect.any(Object),
    )
    expect(afterVerify).toHaveBeenCalledWith(expect.objectContaining({ purpose: 'FIND_ID' }))
  })

  it('aborts and discards pending verification work when cancelled', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof authApi.verifyCode>>>()
    vi.mocked(authApi.verifyCode).mockReturnValueOnce(deferred.promise)
    const afterVerify = vi.fn()
    const flow = useEmailVerificationFlow({
      getEmail: () => 'me@example.com',
      getCode: () => '123456',
      purpose: 'FIND_ID',
      useTimer: false,
      afterVerify,
    })

    const verify = flow.verifyEmailCode()
    const signal = vi.mocked(authApi.verifyCode).mock.calls[0][3]?.signal
    flow.cancelPendingRequests()
    expect(signal?.aborted).toBe(true)
    deferred.resolve(apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'ticket' }))
    await verify

    expect(afterVerify).not.toHaveBeenCalled()
    expect(toastMock.addToast).not.toHaveBeenCalled()
  })
})
