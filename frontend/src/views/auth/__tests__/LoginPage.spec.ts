import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '../LoginPage.vue'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => {
  const router = {
    push: vi.fn(),
  }
  const route = {
    query: {} as Record<string, unknown>,
  }
  const authStore = {
    login: vi.fn(),
  }
  const toastStore = {
    addToast: vi.fn(),
  }

  return {
    router,
    route,
    authStore,
    toastStore,
  }
})

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => mocks.toastStore,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => ({
      'auth.oauth.googleLogin': 'Google 로그인',
      'auth.oauth.discordLogin': 'Discord 로그인',
      'auth.oauth.githubLogin': 'GitHub 로그인',
    }[key] ?? key),
  }),
}))

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mocks.route.query = {}
    mocks.authStore.login.mockResolvedValue(true)
  })

  function mountLoginPage() {
    return mount(LoginPage, {
      global: {
        mocks: {
          $t: (key: string) => ({
            'auth.oauth.googleLogin': 'Google 로그인',
            'auth.oauth.discordLogin': 'Discord 로그인',
            'auth.oauth.githubLogin': 'GitHub 로그인',
          }[key] ?? key),
        },
        stubs: {
          RouterLink: {
            template: '<a><slot /></a>',
          },
        },
      },
    })
  }

  async function submitLogin(wrapper: ReturnType<typeof mountLoginPage>) {
    await wrapper.find('input[type="text"]').setValue('user_1')
    await wrapper.find('input[type="password"]').setValue('password')
    await wrapper.find('form').trigger('submit.prevent')
    await Promise.resolve()
  }

  it('redirects after successful login', async () => {
    const wrapper = mountLoginPage()

    await submitLogin(wrapper)

    expect(mocks.authStore.login).toHaveBeenCalledWith({
      loginId: 'user_1',
      password: 'password',
    }, { signal: expect.any(AbortSignal) })
    expect(mocks.router.push).toHaveBeenCalledWith('/')
  })

  it('does not redirect when auth store reports unsuccessful login', async () => {
    mocks.authStore.login.mockResolvedValueOnce(false)
    const wrapper = mountLoginPage()

    await submitLogin(wrapper)

    expect(mocks.router.push).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error', 3000, 'top-center')
  })

  it('keeps the latest successful submission stable when an older request settles later', async () => {
    const first = createDeferred<boolean>()
    const second = createDeferred<boolean>()
    mocks.authStore.login
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const wrapper = mountLoginPage()

    await submitLogin(wrapper)
    const firstSignal = mocks.authStore.login.mock.calls[0][1].signal as AbortSignal
    await wrapper.find('form').trigger('submit.prevent')
    await Promise.resolve()

    expect(firstSignal.aborted).toBe(true)
    second.resolve(true)
    await Promise.resolve()
    await Promise.resolve()
    expect(mocks.router.push).toHaveBeenCalledTimes(1)
    expect(mocks.router.push).toHaveBeenCalledWith('/')

    first.resolve(false)
    await Promise.resolve()
    await Promise.resolve()
    expect(mocks.router.push).toHaveBeenCalledTimes(1)
    expect(mocks.toastStore.addToast).not.toHaveBeenCalled()
  })

  it('aborts login and ignores its late result when the page scope is disposed', async () => {
    const pending = createDeferred<boolean>()
    mocks.authStore.login.mockReturnValueOnce(pending.promise)
    const wrapper = mountLoginPage()

    await submitLogin(wrapper)
    const signal = mocks.authStore.login.mock.calls[0][1].signal as AbortSignal
    wrapper.unmount()

    expect(signal.aborted).toBe(true)
    pending.resolve(false)
    await Promise.resolve()
    await Promise.resolve()
    expect(mocks.router.push).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).not.toHaveBeenCalled()
  })

  it('exposes credential autocomplete hints and descriptive social login labels', () => {
    const wrapper = mountLoginPage()

    expect(wrapper.get('#login-id').attributes('autocomplete')).toBe('username')
    expect(wrapper.get('#password').attributes('autocomplete')).toBe('current-password')
    expect(wrapper.get('a[aria-label="Google 로그인"]').attributes('href')).toBe('/oauth2/authorization/google')
    expect(wrapper.get('a[aria-label="Discord 로그인"]').attributes('href')).toBe('/oauth2/authorization/discord')
    expect(wrapper.get('a[aria-label="GitHub 로그인"]').attributes('href')).toBe('/oauth2/authorization/github')
  })
})
