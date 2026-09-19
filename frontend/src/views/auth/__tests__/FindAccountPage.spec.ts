import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FindAccountPage from '../FindAccountPage.vue'

const mocks = vi.hoisted(() => ({
  router: { push: vi.fn() },
  findId: vi.fn(),
  completeVerification: vi.fn(),
  resetPassword: vi.fn(),
  cancelPasswordResetRequests: vi.fn(),
  cancelPendingRequests: vi.fn(),
  cancelFindIdRequests: vi.fn(),
  findOptions: null as Record<string, (...args: never[]) => unknown> | null,
  passwordOptions: null as Record<string, (...args: never[]) => unknown> | null,
  verificationOptions: null as Record<string, (...args: never[]) => unknown> | null,
}))

vi.mock('vue-router', () => ({ useRouter: () => mocks.router }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/composables/useFindIdFlow', () => ({
  useFindIdFlow: (options: Record<string, (...args: never[]) => unknown>) => {
    mocks.findOptions = options
    return { findId: mocks.findId, cancelPendingRequests: mocks.cancelFindIdRequests }
  },
}))
vi.mock('@/composables/usePasswordResetByVerificationFlow', () => ({
  usePasswordResetByVerificationFlow: (options: Record<string, (...args: never[]) => unknown>) => {
    mocks.passwordOptions = options
    return {
      completeVerification: mocks.completeVerification,
      resetPassword: mocks.resetPassword,
      cancelPendingRequests: mocks.cancelPasswordResetRequests,
    }
  },
}))
vi.mock('@/composables/useAuthEmailVerificationSection', () => ({
  useAuthEmailVerificationSection: (options: Record<string, (...args: never[]) => unknown>) => {
    mocks.verificationOptions = options
    return {
      sectionProps: {},
      sendVerifyCode: vi.fn(),
      verifyEmailCode: vi.fn(),
      cancelPendingRequests: mocks.cancelPendingRequests,
    }
  },
}))

function mountPage() {
  return mount(FindAccountPage, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        AuthFormShell: { template: '<div><slot /></div>' },
        AuthEmailVerificationSection: {
          props: ['email'],
          template: '<div data-verification><input data-email :value="email" @input="$emit(\'update:email\', $event.target.value)" /></div>',
        },
        AuthPasswordPairFields: {
          props: ['password', 'confirmPassword'],
          template: '<div data-password-fields><input data-password :value="password" @input="$emit(\'update:password\', $event.target.value)" /><input data-confirm :value="confirmPassword" @input="$emit(\'update:confirmPassword\', $event.target.value)" /></div>',
        },
        BaseButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        BaseSegmentedControl: {
          template: '<button data-password-tab @click="$emit(\'update:modelValue\', \'password\')">tab</button>',
        },
      },
    },
  })
}

describe('FindAccountPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.findOptions = null
    mocks.passwordOptions = null
    mocks.verificationOptions = null
  })

  it('runs the id recovery callbacks and navigates to login', async () => {
    const wrapper = mountPage()
    const verification = mocks.verificationOptions!
    const find = mocks.findOptions!

    expect(verification.getEmail()).toBe('')
    expect(verification.getCode()).toBe('')
    expect(verification.purpose()).toBe('FIND_ID')
    verification.onLoadingChange(true as never)
    verification.afterSend()
    await verification.afterVerify({ verificationTicket: 'ticket-1', purpose: 'FIND_ID' } as never)
    expect(mocks.findId).toHaveBeenCalledWith('ticket-1')

    find.onLoadingChange(false as never)
    find.onSuccess({ loginId: 'noviis', verificationTicket: 'ticket-2' } as never)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('noviis')
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(mocks.router.push).toHaveBeenCalledWith('/login')
  })

  it('shows verification again after ticket expiry while preserving email and passwords', async () => {
    const wrapper = mountPage()
    await wrapper.find('[data-password-tab]').trigger('click')
    await wrapper.find('[data-email]').setValue('user@example.com')
    mocks.passwordOptions!.onVerified('expired-ticket' as never)
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-password]').setValue('Password1!')
    await wrapper.find('[data-confirm]').setValue('Password1!')
    expect(wrapper.find('[data-verification]').exists()).toBe(false)

    mocks.passwordOptions!.onVerificationExpired()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-verification]').exists()).toBe(true)
    expect((wrapper.find('[data-email]').element as HTMLInputElement).value).toBe('user@example.com')
    expect(mocks.passwordOptions!.getVerificationTicket()).toBe('')
    expect(mocks.passwordOptions!.getNewPassword()).toBe('Password1!')
    expect(mocks.passwordOptions!.getConfirmPassword()).toBe('Password1!')
    mocks.passwordOptions!.onVerified('fresh-ticket' as never)
    await wrapper.vm.$nextTick()
    expect((wrapper.find('[data-password]').element as HTMLInputElement).value).toBe('Password1!')
    wrapper.unmount()
  })

  it('switches to password recovery and completes verification', async () => {
    const wrapper = mountPage()
    await wrapper.find('[data-password-tab]').trigger('click')
    const verification = mocks.verificationOptions!
    const password = mocks.passwordOptions!

    expect(verification.purpose()).toBe('PASSWORD_RESET')
    expect(mocks.cancelPendingRequests).toHaveBeenCalledOnce()
    expect(mocks.cancelFindIdRequests).toHaveBeenCalledOnce()
    expect(mocks.cancelPasswordResetRequests).toHaveBeenCalledOnce()
    verification.afterSend()
    await verification.afterVerify({ verificationTicket: 'reset-ticket', purpose: 'PASSWORD_RESET' } as never)
    expect(mocks.completeVerification).toHaveBeenCalledWith('reset-ticket')

    password.onLoadingChange(true as never)
    password.onVerified('verified-ticket' as never)
    expect(password.getEmail()).toBe('')
    expect(password.getVerificationTicket()).toBe('verified-ticket')
    expect(password.getNewPassword()).toBe('')
    expect(password.getConfirmPassword()).toBe('')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-password-fields]').exists()).toBe(true)
  })
})
