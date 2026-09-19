import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useEmailVerificationFlow } from '../useEmailVerificationFlow'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import { effectScope } from 'vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))

const authStoreMock = vi.hoisted(() => ({
  fetchUser: vi.fn(),
  sessionGeneration: 0,
  user: { userId: 1, isEmailVerified: false }
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
    authStoreMock.sessionGeneration = 0
    authStoreMock.user.userId = 1
    authStoreMock.user.isEmailVerified = false
  })

  it('resets a completed verification and allows sending a new code for the same email', async () => {
    const scope = effectScope()
    const flow = scope.run(() => useEmailVerificationFlow({
      getEmail: () => 'signup@example.com', purpose: 'SIGNUP',
    }))!
    Object.assign(flow.emailVerification, {
      code: '123456', verificationTicket: 'expired-ticket', isVerified: true, isCodeSent: true,
    })
    vi.mocked(authApi.sendVerificationCode).mockResolvedValue(apiSuccessResponse<typeof authApi.sendVerificationCode>())

    flow.resetEmailVerification()
    expect(flow.emailVerification).toMatchObject({
      email: 'signup@example.com', code: '', verificationTicket: '',
      isVerified: false, isCodeSent: false, resendCooldown: 0,
    })
    await flow.sendVerifyCode()
    expect(authApi.sendVerificationCode).toHaveBeenCalledWith('signup@example.com', 'SIGNUP', {
      signal: expect.any(AbortSignal),
    })
    expect(flow.emailVerification.isCodeSent).toBe(true)
    scope.stop()
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

  it.each(['old@example.com', ''])('uses the edited modal address for send, resend and verification (profile=%s)', async (profileEmail) => {
    vi.mocked(authApi.sendVerificationCode).mockResolvedValue(apiSuccessResponse<typeof authApi.sendVerificationCode>())
    vi.mocked(authApi.verifyCode).mockResolvedValue(apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'new-email-ticket' }))
    vi.mocked(userApi.verifyEmail).mockResolvedValue(apiSuccessResponse<typeof userApi.verifyEmail>())
    authStoreMock.fetchUser.mockResolvedValue(true)
    const flow = useEmailVerificationFlow({ getEmail: () => profileEmail, useTimer: false })
    flow.openVerifyModal()
    flow.emailVerification.email = ' new@example.com '
    await flow.sendVerifyCode()
    await flow.sendVerifyCode()

    expect(authApi.sendVerificationCode).toHaveBeenCalledTimes(2)
    expect(authApi.sendVerificationCode).toHaveBeenNthCalledWith(1, 'new@example.com', 'CHANGE_EMAIL', expect.any(Object))
    expect(authApi.sendVerificationCode).toHaveBeenNthCalledWith(2, 'new@example.com', 'CHANGE_EMAIL', expect.any(Object))
    flow.emailVerification.code = '123456'
    await flow.verifyEmailCode()

    expect(authApi.verifyCode).toHaveBeenCalledWith('new@example.com', '123456', 'CHANGE_EMAIL', expect.any(Object))
    expect(userApi.verifyEmail).toHaveBeenCalledWith({ email: 'new@example.com', verificationTicket: 'new-email-ticket' }, expect.any(Object))
    expect(flow.isVerifyModalOpen.value).toBe(false)
  })

  it('does not fall back to the profile address when the modal address is cleared', async () => {
    const flow = useEmailVerificationFlow({ getEmail: () => 'old@example.com', useTimer: false })
    flow.openVerifyModal()
    flow.emailVerification.email = ''
    await flow.sendVerifyCode()

    expect(authApi.sendVerificationCode).not.toHaveBeenCalled()
    expect(flow.emailVerification.email).toBe('')
    expect(toastMock.addToast).toHaveBeenCalledWith('auth.emailRequired', 'error')
    flow.closeVerifyModal()
  })

  it.each(['SIGNUP', 'FIND_ID', 'PASSWORD_RESET'] as const)('reads the current external form address for inline %s verification', async (purpose) => {
    let email = 'first@example.com'
    vi.mocked(authApi.sendVerificationCode).mockResolvedValue(apiSuccessResponse<typeof authApi.sendVerificationCode>())
    vi.mocked(authApi.verifyCode).mockResolvedValue(apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'inline-ticket' }))
    const flow = useEmailVerificationFlow({ getEmail: () => email, getCode: () => '123456', purpose, useTimer: false })
    await flow.sendVerifyCode()
    email = 'second@example.com'
    await flow.sendVerifyCode()
    await flow.verifyEmailCode()

    expect(authApi.sendVerificationCode).toHaveBeenLastCalledWith('second@example.com', purpose, expect.any(Object))
    expect(authApi.verifyCode).toHaveBeenCalledWith('second@example.com', '123456', purpose, expect.any(Object))
    expect(userApi.verifyEmail).not.toHaveBeenCalled()
    flow.closeVerifyModal()
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
    expect(flow.emailVerification.code).toBe('')
    expect(flow.emailVerification.verificationTicket).toBe('')
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

  it('aborts and clears sensitive verification state when the auth session changes', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof authApi.verifyCode>>>()
    vi.mocked(authApi.verifyCode).mockReturnValueOnce(deferred.promise)
    const scope = effectScope()
    const flow = scope.run(() => useEmailVerificationFlow({
      getEmail: () => 'first@example.com',
      getCode: () => '123456',
      useTimer: false,
    }))!
    flow.openVerifyModal()

    const verify = flow.verifyEmailCode()
    const signal = vi.mocked(authApi.verifyCode).mock.calls[0][3]?.signal
    authStoreMock.sessionGeneration = 1
    authStoreMock.user.userId = 2
    notifyAuthSessionBoundary(1)

    expect(signal?.aborted).toBe(true)
    expect(flow.isVerifyModalOpen.value).toBe(false)
    expect(flow.emailVerification.email).toBe('')
    expect(flow.emailVerification.code).toBe('')
    expect(flow.emailVerification.verificationTicket).toBe('')

    deferred.resolve(apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'stale-ticket' }))
    await verify
    expect(userApi.verifyEmail).not.toHaveBeenCalled()
    expect(authStoreMock.fetchUser).not.toHaveBeenCalled()
    expect(toastMock.addToast).not.toHaveBeenCalled()

    scope.stop()
  })
})
