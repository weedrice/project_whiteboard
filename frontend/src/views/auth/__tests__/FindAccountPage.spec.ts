import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FindAccountPage from '../FindAccountPage.vue'

const mocks = vi.hoisted(() => ({
  router: {
    push: vi.fn(),
  },
  emailVerificationOptions: [] as Array<Record<string, unknown>>,
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/composables/useFindIdFlow', () => ({
  useFindIdFlow: () => ({
    findId: vi.fn(),
  }),
}))

vi.mock('@/composables/usePasswordResetByVerificationFlow', () => ({
  usePasswordResetByVerificationFlow: () => ({
    completeVerification: vi.fn(),
    resetPassword: vi.fn(),
  }),
}))

vi.mock('@/composables/useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: (options: Record<string, unknown>) => {
    mocks.emailVerificationOptions.push(options)
    return {
      sendVerifyCode: vi.fn(),
      verifyEmailCode: vi.fn(),
    }
  },
}))

describe('FindAccountPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.emailVerificationOptions.length = 0
  })

  function mountPage() {
    return mount(FindAccountPage, {
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

  it('lets email verification flow show the default send error toast', () => {
    mountPage()

    expect(mocks.emailVerificationOptions).toHaveLength(1)
    expect(mocks.emailVerificationOptions[0]).not.toHaveProperty('onSendError')
  })
})
