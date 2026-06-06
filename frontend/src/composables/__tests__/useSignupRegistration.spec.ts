import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '@/api/auth'
import { useSignupRegistration } from '../useSignupRegistration'

const mocks = vi.hoisted(() => {
  const toastStore = {
    addToast: vi.fn()
  }
  const router = {
    push: vi.fn()
  }
  const verification = {
    email: '',
    code: '',
    verificationTicket: '',
    isCodeSent: false,
    isVerified: false,
    loading: false,
    timeLeft: 0,
    resendCooldown: 0
  }
  let flowOptions: {
    beforeSend?: (email: string) => Promise<void> | void
    afterVerify?: (context: { response: { isReregister: boolean; loginId?: string } }) => void
  } | null = null

  return {
    toastStore,
    router,
    verification,
    get flowOptions() {
      return flowOptions
    },
    setFlowOptions: (options: typeof flowOptions) => {
      flowOptions = options
    }
  }
})

vi.mock('@/stores/toast', () => ({
  useToastStore: () => mocks.toastStore
}))

vi.mock('@/api/auth', () => ({
  authApi: {
    checkEmailForReregister: vi.fn(),
    signup: vi.fn()
  }
}))

vi.mock('@/composables/useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: (options: typeof mocks.flowOptions) => {
    mocks.setFlowOptions(options)
    return {
      emailVerification: mocks.verification,
      formatVerifyTime: (seconds: number) => String(seconds),
      sendVerifyCode: vi.fn(async () => {
        await options?.beforeSend?.('user@example.com')
      }),
      verifyEmailCode: vi.fn(async () => {
        options?.afterVerify?.({
          response: {
            isReregister: true,
            loginId: 'restored-login'
          }
        })
      })
    }
  }
}))

type SignupRegistration = ReturnType<typeof useSignupRegistration>

function mountSignupRegistration(routeQuery: Record<string, unknown> = {}) {
  let composable = undefined as unknown as SignupRegistration
  const route = {
    query: routeQuery
  }

  const wrapper = mount(defineComponent({
    setup() {
      composable = useSignupRegistration({
        route: route as never,
        router: mocks.router as never,
        t: (key: string) => key
      })
      return () => null
    }
  }))

  return {
    wrapper,
    composable
  }
}

async function flushMountedAsync() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('useSignupRegistration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.setFlowOptions(null)
    Object.assign(mocks.verification, {
      email: '',
      code: '',
      verificationTicket: '',
      isCodeSent: false,
      isVerified: false,
      loading: false,
      timeLeft: 0,
      resendCooldown: 0
    })
    vi.mocked(authApi.checkEmailForReregister).mockResolvedValue({
      data: {
        success: true,
        data: {
          canReregister: false
        }
      }
    } as never)
    vi.mocked(authApi.signup).mockResolvedValue({
      data: {
        success: true
      }
    } as never)
  })

  it('hydrates query email/name and masked reregister login on mount', async () => {
    vi.mocked(authApi.checkEmailForReregister).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          canReregister: true,
          maskedLoginId: 'ma***ed'
        }
      }
    } as never)

    const { composable, wrapper } = mountSignupRegistration({
      email: 'user@example.com',
      name: 'Display'
    })
    await flushMountedAsync()

    expect(composable.form.value.email).toBe('user@example.com')
    expect(composable.form.value.displayName).toBe('Display')
    expect(composable.form.value.loginId).toBe('ma***ed')
    expect(composable.isReregister.value).toBe(true)
    wrapper.unmount()
  })

  it('runs reregister lookup before sending verification and replaces login id after verify', async () => {
    vi.mocked(authApi.checkEmailForReregister).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          canReregister: true,
          maskedLoginId: 'ma***ed'
        }
      }
    } as never)
    const { composable, wrapper } = mountSignupRegistration()

    await composable.sendVerificationCode()
    await composable.verifyCode()

    expect(authApi.checkEmailForReregister).toHaveBeenCalledWith('user@example.com')
    expect(composable.form.value.loginId).toBe('restored-login')
    wrapper.unmount()
  })

  it('submits the signup payload without passwordConfirm and preserves provider query values', async () => {
    const { composable, wrapper } = mountSignupRegistration({
      provider: 'google',
      providerId: 'oauth-1'
    })
    Object.assign(composable.form.value, {
      loginId: 'login_1',
      password: 'Password1!',
      passwordConfirm: 'Password1!',
      email: 'user@example.com',
      displayName: 'Display'
    })
    composable.verification.isVerified = true
    composable.verification.verificationTicket = 'ticket-1'

    await composable.handleSignup()

    expect(authApi.signup).toHaveBeenCalledWith({
      loginId: 'login_1',
      password: 'Password1!',
      email: 'user@example.com',
      displayName: 'Display',
      verificationTicket: 'ticket-1',
      provider: 'google',
      providerId: 'oauth-1'
    })
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.signupSuccess', 'success')
    expect(mocks.router.push).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })

  it('uses the first route query value when signup query params are arrays', async () => {
    const { composable, wrapper } = mountSignupRegistration({
      provider: ['google', 'github'],
      providerId: ['oauth-1', 'oauth-2'],
      email: ['user@example.com', 'other@example.com'],
      name: ['Display', 'Other']
    })
    await flushMountedAsync()

    Object.assign(composable.form.value, {
      loginId: 'login_1',
      password: 'Password1!',
      passwordConfirm: 'Password1!',
      email: composable.form.value.email,
      displayName: composable.form.value.displayName
    })
    composable.verification.isVerified = true
    composable.verification.verificationTicket = 'ticket-1'

    await composable.handleSignup()

    expect(composable.form.value.email).toBe('user@example.com')
    expect(composable.form.value.displayName).toBe('Display')
    expect(authApi.checkEmailForReregister).toHaveBeenCalledWith('user@example.com')
    expect(authApi.signup).toHaveBeenCalledWith(expect.objectContaining({
      provider: 'google',
      providerId: 'oauth-1'
    }))
    wrapper.unmount()
  })

  it('blocks submit until email verification has a ticket', async () => {
    const { composable, wrapper } = mountSignupRegistration()
    Object.assign(composable.form.value, {
      loginId: 'login_1',
      password: 'Password1!',
      passwordConfirm: 'Password1!',
      email: 'user@example.com',
      displayName: 'Display'
    })

    await composable.handleSignup()

    expect(authApi.signup).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.verificationRequired', 'error')
    wrapper.unmount()
  })
})
