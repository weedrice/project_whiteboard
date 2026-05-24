import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResetPasswordPage from '../ResetPasswordPage.vue'

const mocks = vi.hoisted(() => {
  const router = {
    push: vi.fn(),
  }
  const route = {
    query: {} as Record<string, unknown>,
  }
  const toastStore = {
    addToast: vi.fn(),
  }

  return {
    router,
    route,
    toastStore,
  }
})

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => mocks.toastStore,
}))

vi.mock('@/api/auth', () => ({
  authApi: {
    resetPasswordWithToken: vi.fn(),
  },
}))

vi.mock('@/composables/useAuthPasswordValidation', () => ({
  useAuthPasswordValidation: () => ({
    validatePasswordPair: vi.fn(() => null),
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.query = {}
  })

  function mountPage() {
    return mount(ResetPasswordPage, {
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

  it('shows a clear heading for invalid reset links', () => {
    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('auth.resetPasswordTitle')
    expect(wrapper.text()).toContain('auth.invalidResetLink')
  })

  it('exposes password-manager attributes on reset fields', () => {
    mocks.route.query = { token: 'reset-token' }

    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('auth.resetPasswordTitle')
    expect(wrapper.get('#new-password').attributes()).toMatchObject({
      name: 'new-password',
      autocomplete: 'new-password',
    })
    expect(wrapper.get('#confirm-password').attributes()).toMatchObject({
      name: 'confirm-password',
      autocomplete: 'new-password',
    })
  })
})
