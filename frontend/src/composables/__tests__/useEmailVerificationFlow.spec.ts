import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useEmailVerificationFlow } from '../useEmailVerificationFlow'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))

const authStoreMock = vi.hoisted(() => ({
  fetchUser: vi.fn()
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

    expect(authApi.sendVerificationCode).toHaveBeenCalledWith('me@example.com', 'CHANGE_EMAIL')
    expect(flow.emailVerification.isCodeSent).toBe(true)
  })

  it('verifies email then refreshes dashboard profile and global auth user', async () => {
    vi.mocked(authApi.verifyCode).mockResolvedValue(
      apiSuccessDataResponse<typeof authApi.verifyCode>({ verificationTicket: 'ticket-1' })
    )
    vi.mocked(userApi.verifyEmail).mockResolvedValue(apiSuccessResponse<typeof userApi.verifyEmail>())
    authStoreMock.fetchUser.mockResolvedValue(true)
    const refreshProfile = vi.fn().mockResolvedValue(undefined)
    const flow = useEmailVerificationFlow({
      getEmail: () => 'me@example.com',
      refreshProfile
    })
    flow.openVerifyModal()
    flow.emailVerification.code = '123456'
    flow.emailVerification.timeLeft = 60

    await flow.verifyEmailCode()

    expect(authApi.verifyCode).toHaveBeenCalledWith('me@example.com', '123456', 'CHANGE_EMAIL')
    expect(userApi.verifyEmail).toHaveBeenCalledWith({
      email: 'me@example.com',
      verificationTicket: 'ticket-1'
    })
    expect(refreshProfile).toHaveBeenCalled()
    expect(authStoreMock.fetchUser).toHaveBeenCalled()
    expect(flow.isVerifyModalOpen.value).toBe(false)
  })
})
