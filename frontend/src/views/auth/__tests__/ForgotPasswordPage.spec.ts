import { mount } from '@vue/test-utils'
import { defineComponent, h, type Component } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ForgotPasswordPage from '../ForgotPasswordPage.vue'

const routerPush = vi.fn()
const addToast = vi.fn()
let emailVerificationOptions: any

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPush,
  }),
  RouterLink: defineComponent({
    setup(_props, { slots }) {
      return () => h('a', slots.default?.())
    },
  }),
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: (key: string) => key,
    },
  }),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/api/auth', () => ({
  authApi: {
    sendPasswordResetLinkByEmail: vi.fn(),
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast,
  }),
}))

vi.mock('@/composables/useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: (options: any) => {
    emailVerificationOptions = options
    return {
      sendVerifyCode: vi.fn(),
      verifyEmailCode: vi.fn(),
    }
  },
}))

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: {
    modelValue: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('input', {
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    })
  },
})

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  setup(_props, { slots }) {
    return () => h('button', slots.default?.())
  },
})

const mountPage = () => mount(ForgotPasswordPage, {
  global: {
    stubs: {
      BaseInput: BaseInputStub,
      BaseButton: BaseButtonStub,
      ChevronLeft: true,
      Mail: true,
      CheckCircle: true,
      RouterLink: true,
    } as Record<string, Component | boolean>,
  },
})

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    emailVerificationOptions = null
  })

  it('marks deleted-account verification errors as handled and redirects to signup', async () => {
    const wrapper = mountPage()
    await wrapper.get('input').setValue(' deleted+user@example.com ')
    const error = {
      response: {
        data: {
          error: {
            code: 'A009',
            message: 'deleted',
          },
        },
      },
    }

    const handled = emailVerificationOptions.onVerifyError(error)

    expect(handled).toBe(true)
    expect(addToast).toHaveBeenCalledWith('auth.userDeleted', 'info')
    expect(routerPush).toHaveBeenCalledWith('/signup?email=deleted%2Buser%40example.com')
  })

  it('leaves unrelated verification errors for the shared email flow', () => {
    mountPage()
    const error = {
      response: {
        data: {
          error: {
            code: 'A001',
            message: 'failed',
          },
        },
      },
    }

    expect(emailVerificationOptions.onVerifyError(error)).toBe(false)
    expect(addToast).not.toHaveBeenCalled()
    expect(routerPush).not.toHaveBeenCalled()
  })
})
