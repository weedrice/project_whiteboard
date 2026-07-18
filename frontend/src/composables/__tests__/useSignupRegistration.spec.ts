import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { authApi } from '@/api/auth'
import { apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
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
    getOAuthSignupTicket: vi.fn(),
    deleteOAuthSignupTicket: vi.fn(),
    signup: vi.fn()
  }
}))

vi.mock('@/composables/useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: (options: typeof mocks.flowOptions) => {
    mocks.setFlowOptions(options)
    return {
      emailVerification: mocks.verification,
      formatVerifyTime: (seconds: number) => String(seconds),
      sendVerifyCode: vi.fn(async () => undefined),
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
    query: routeQuery,
    fullPath: '/signup',
  }

  const wrapper = mount(defineComponent({
    setup() {
      composable = useSignupRegistration({
        route: route as RouteLocationNormalizedLoaded,
        router: mocks.router as Partial<Router> as Router,
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
    vi.mocked(authApi.getOAuthSignupTicket).mockRejectedValue({ response: { status: 400 } })
    vi.mocked(authApi.signup).mockResolvedValue(apiSuccessResponse<typeof authApi.signup>())
    vi.mocked(authApi.deleteOAuthSignupTicket).mockResolvedValue(apiSuccessResponse<typeof authApi.deleteOAuthSignupTicket>())
  })

  it('hydrates query email and name without performing a reregister lookup', async () => {
    const { composable, wrapper } = mountSignupRegistration({
      email: 'user@example.com',
      name: 'Display'
    })
    await flushMountedAsync()

    expect(composable.form.value.email).toBe('user@example.com')
    expect(composable.form.value.displayName).toBe('Display')
    expect(composable.form.value.loginId).toBe('')
    expect(composable.isReregister.value).toBe(false)
    expect(authApi.checkEmailForReregister).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('learns reregister state only from the verified response', async () => {
    const { composable, wrapper } = mountSignupRegistration()

    await composable.sendVerificationCode()
    await composable.verifyCode()

    expect(authApi.checkEmailForReregister).not.toHaveBeenCalled()
    expect(composable.form.value.loginId).toBe('restored-login')
    expect(composable.isReregister.value).toBe(true)
    wrapper.unmount()
  })

  it('submits without exposing the OAuth registration ticket in the request body', async () => {
    vi.mocked(authApi.getOAuthSignupTicket).mockResolvedValue(
      apiSuccessDataResponse<typeof authApi.getOAuthSignupTicket>({
        email: 'oauth@example.com',
        name: 'OAuth User',
        provider: 'google',
      })
    )
    const { composable, wrapper } = mountSignupRegistration()
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

    expect(authApi.signup).toHaveBeenCalledWith({
      loginId: 'login_1',
      password: 'Password1!',
      email: 'oauth@example.com',
      displayName: 'OAuth User',
      verificationTicket: 'ticket-1',
    }, { signal: expect.any(AbortSignal) })
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.signupSuccess', 'success')
    expect(mocks.router.push).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })

  it('uses the first route query value when signup query params are arrays', async () => {
    const { composable, wrapper } = mountSignupRegistration({
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
    expect(authApi.checkEmailForReregister).not.toHaveBeenCalled()
    expect(authApi.signup).toHaveBeenCalledWith(
      expect.not.objectContaining({ oauthRegistrationTicket: expect.anything() }),
      { signal: expect.any(AbortSignal) },
    )
    wrapper.unmount()
  })

  it('blocks submit until email verification has a ticket', async () => {
    const { composable, wrapper } = mountSignupRegistration()
    await flushMountedAsync()
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

  it('keeps the form blocked and exposes retry when the OAuth ticket lookup fails transiently', async () => {
    vi.mocked(authApi.getOAuthSignupTicket).mockRejectedValueOnce({ response: { status: 503 } })
    const { composable, wrapper } = mountSignupRegistration({ email: 'query@example.com' })
    await flushMountedAsync()

    expect(composable.oauthTicketLoadError.value).toBe(true)
    expect(composable.form.value.email).toBe('')

    await composable.handleSignup()
    expect(authApi.signup).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.oauthTicket.loadFailed', 'error')
    wrapper.unmount()
  })

  it('clears the server OAuth ticket before explicitly returning to login', async () => {
    const { composable, wrapper } = mountSignupRegistration()
    await flushMountedAsync()

    await composable.cancelOAuthSignup()

    expect(authApi.deleteOAuthSignupTicket).toHaveBeenCalledWith({ signal: expect.any(AbortSignal) })
    expect(mocks.router.push).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })

  it('aborts an in-flight signup and ignores its late success after unmount', async () => {
    const pending = createDeferred<Awaited<ReturnType<typeof authApi.signup>>>()
    vi.mocked(authApi.signup).mockReturnValue(pending.promise)
    const { composable, wrapper } = mountSignupRegistration()
    await flushMountedAsync()
    Object.assign(composable.form.value, {
      loginId: 'login_1',
      password: 'Password1!',
      passwordConfirm: 'Password1!',
      email: 'user@example.com',
      displayName: 'Display',
    })
    composable.verification.isVerified = true
    composable.verification.verificationTicket = 'ticket-1'

    const request = composable.handleSignup()
    const config = vi.mocked(authApi.signup).mock.calls[0]?.[1]
    wrapper.unmount()
    expect(config?.signal?.aborted).toBe(true)
    pending.resolve(apiSuccessResponse<typeof authApi.signup>())
    await request

    expect(mocks.router.push).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).not.toHaveBeenCalledWith('auth.signupSuccess', 'success')
  })
})
