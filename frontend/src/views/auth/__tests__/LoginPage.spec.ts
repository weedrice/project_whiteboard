import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '../LoginPage.vue'

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
    t: (key: string) => key,
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
          $t: (key: string) => key,
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
    })
    expect(mocks.router.push).toHaveBeenCalledWith('/')
  })

  it('does not redirect when auth store reports unsuccessful login', async () => {
    mocks.authStore.login.mockResolvedValueOnce(false)
    const wrapper = mountLoginPage()

    await submitLogin(wrapper)

    expect(mocks.router.push).not.toHaveBeenCalled()
    expect(mocks.toastStore.addToast).toHaveBeenCalledWith('auth.loginFailed', 'error', 3000, 'top-center')
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
