import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import EmailVerificationModal from '../EmailVerificationModal.vue'
import type { EmailVerificationState } from '@/composables/useEmailVerificationState'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const BaseModalStub = {
  props: ['isOpen', 'title'],
  emits: ['close'],
  template: `
    <section v-if="isOpen">
      <div data-test="modal-body"><slot /></div>
      <footer v-if="$slots.footer" data-test="modal-footer"><slot name="footer" /></footer>
    </section>
  `,
}

const BaseInputStub = {
  props: [
    'modelValue',
    'id',
    'name',
    'autocomplete',
    'label',
    'placeholder',
    'disabled',
    'error',
  ],
  emits: ['update:modelValue'],
  inheritAttrs: false,
  template: `
    <label>
      {{ label }}
      <input
        :id="id"
        :name="name"
        :autocomplete="autocomplete"
        :value="modelValue"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        v-bind="$attrs"
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <span v-if="error" data-testid="input-error">{{ error }}</span>
    </label>
  `,
}

const BaseButtonStub = {
  props: ['disabled', 'loading', 'variant'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
}

function createVerification(overrides: Partial<EmailVerificationState> = {}) {
  return reactive<EmailVerificationState>({
    email: '',
    code: '',
    verificationTicket: '',
    isCodeSent: false,
    isVerified: false,
    loading: false,
    timeLeft: 0,
    resendCooldown: 0,
    ...overrides,
  })
}

function mountModal(verification: EmailVerificationState) {
  return mount(EmailVerificationModal, {
    props: {
      isOpen: true,
      verification,
      formatVerifyTime: (seconds: number) => `${seconds}s`,
      isValidEmail: (value: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value),
    },
    global: {
      stubs: {
        BaseModal: BaseModalStub,
        BaseInput: BaseInputStub,
        BaseButton: BaseButtonStub,
        ShieldCheck: true,
      },
    },
  })
}

describe('EmailVerificationModal', () => {
  it('renders the final verification action directly in the modal footer', () => {
    const wrapper = mountModal(createVerification({
      email: 'user@example.com',
      code: '123456',
      isCodeSent: true,
      timeLeft: 180,
    }))

    const footer = wrapper.get('[data-test="modal-footer"]')
    expect(Array.from(footer.element.children).map((child) => child.tagName)).toEqual(['BUTTON'])
    expect(footer.get('button').text()).toBe('auth.verifyCode')
    expect(wrapper.get('[data-test="modal-body"]').text()).not.toContain('auth.verifyCode')
  })

  it('validates email display state with trimmed input', () => {
    const wrapper = mountModal(createVerification({ email: ' user@example.com ' }))

    expect(wrapper.find('[data-testid="input-error"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
  })

  it('shows an email format error for non-blank invalid email', () => {
    const wrapper = mountModal(createVerification({ email: ' invalid ' }))

    expect(wrapper.get('[data-testid="input-error"]').text()).toBe('auth.validation.emailFormat')
    expect(wrapper.get('[role="alert"]').text()).toBe('auth.validation.emailFormat')
    expect(wrapper.get('[role="alert"]').attributes('id')).toBe('email-verification-email-error')
    expect(wrapper.get('#email-verification-email').attributes('aria-describedby')).toBe('email-verification-email-error')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })
})
