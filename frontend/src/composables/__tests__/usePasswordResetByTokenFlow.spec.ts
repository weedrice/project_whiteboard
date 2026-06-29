import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePasswordResetByTokenFlow } from '../usePasswordResetByTokenFlow'
import { authApi } from '@/api/auth'

const routerPush = vi.hoisted(() => vi.fn())
const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))
const passwordValidationMock = vi.hoisted(() => vi.fn())

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
    resetPasswordWithToken: vi.fn(),
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => toastMock,
}))

vi.mock('@/composables/useAuthPasswordValidation', () => ({
  useAuthPasswordValidation: () => ({
    validatePasswordPair: passwordValidationMock,
  }),
}))

describe('usePasswordResetByTokenFlow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    passwordValidationMock.mockReturnValue(null)
  })

  const createFlow = (overrides: {
    token?: string
    newPassword?: string
    confirmPassword?: string
  } = {}) => usePasswordResetByTokenFlow({
    token: ref(overrides.token ?? 'reset-token'),
    newPassword: ref(overrides.newPassword ?? 'Password1!'),
    confirmPassword: ref(overrides.confirmPassword ?? 'Password1!'),
  })

  it('rejects missing reset tokens before validation or API calls', async () => {
    const { resetPassword } = createFlow({ token: '' })

    await resetPassword()

    expect(toastMock.addToast).toHaveBeenCalledWith('auth.invalidResetLink', 'error')
    expect(passwordValidationMock).not.toHaveBeenCalled()
    expect(authApi.resetPasswordWithToken).not.toHaveBeenCalled()
  })

  it('does not call reset API when password validation fails', async () => {
    passwordValidationMock.mockReturnValueOnce('auth.passwordMismatch')
    const { resetPassword } = createFlow({ confirmPassword: 'OtherPassword1!' })

    await resetPassword()

    expect(passwordValidationMock).toHaveBeenCalledWith('Password1!', 'OtherPassword1!', {
      requirePassword: true,
      messages: {
        required: 'auth.placeholders.password',
        invalid: 'auth.validation.passwordStrength',
        mismatch: 'auth.passwordMismatch',
      },
    })
    expect(toastMock.addToast).toHaveBeenCalledWith('auth.passwordMismatch', 'error')
    expect(authApi.resetPasswordWithToken).not.toHaveBeenCalled()
  })

  it('resets password with token and redirects to login on success', async () => {
    vi.mocked(authApi.resetPasswordWithToken).mockResolvedValue({
      data: { success: true }
    } as never)
    const { isLoading, resetPassword } = createFlow()

    const request = resetPassword()
    expect(isLoading.value).toBe(true)
    await request

    expect(authApi.resetPasswordWithToken).toHaveBeenCalledWith('reset-token', 'Password1!')
    expect(toastMock.addToast).toHaveBeenCalledWith('auth.passwordResetSuccess', 'success')
    expect(routerPush).toHaveBeenCalledWith('/login')
    expect(isLoading.value).toBe(false)
  })

  it('shows extracted API error messages and clears loading state', async () => {
    vi.mocked(authApi.resetPasswordWithToken).mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          error: {
            message: 'expired token'
          }
        }
      }
    })
    const { isLoading, resetPassword } = createFlow()

    await resetPassword()

    expect(toastMock.addToast).toHaveBeenCalledWith('expired token', 'error')
    expect(routerPush).not.toHaveBeenCalled()
    expect(isLoading.value).toBe(false)
  })
})
