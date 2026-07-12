import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import AuthEmailVerificationSection from '../AuthEmailVerificationSection.vue'
import AuthFormShell from '../AuthFormShell.vue'

const RouterLinkStub = defineComponent({
  props: {
    to: String,
  },
  setup(props, { slots }) {
    return () => h('a', { to: props.to }, slots.default?.())
  },
})

const BaseInputStub = defineComponent({
  props: {
    id: String,
    modelValue: String,
    label: String,
    name: String,
    autocomplete: String,
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('input', {
      id: props.id,
      name: props.name,
      autocomplete: props.autocomplete,
      disabled: props.disabled,
      'aria-label': props.label,
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    })
  },
})

const BaseButtonStub = defineComponent({
  props: {
    type: {
      type: String,
      default: 'button',
    },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', { type: props.type, onClick: () => emit('click') }, slots.default?.())
  },
})

describe('auth common components', () => {
  it('uses the full mobile width and constrains content from the small breakpoint', () => {
    const wrapper = mount(AuthFormShell, {
      slots: {
        default: '<form>Content</form>',
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(wrapper.get('.mx-auto').classes()).toEqual(expect.arrayContaining(['w-full', 'sm:w-[80%]']))
  })

  it('renders AuthFormShell header, back link, and constrained content', () => {
    const wrapper = mount(AuthFormShell, {
      props: {
        title: 'Reset password',
        description: 'Enter a new password.',
        backTo: '/login',
        contentWidthClass: 'w-full',
      },
      slots: {
        default: '<form>Content</form>',
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          RouterLink: RouterLinkStub,
          ChevronLeft: true,
        },
      },
    })

    expect(wrapper.get('a').attributes('to')).toBe('/login')
    expect(wrapper.get('h1').text()).toBe('Reset password')
    expect(wrapper.text()).toContain('Enter a new password.')
    expect(wrapper.get('form').text()).toBe('Content')
  })

  it('emits email verification updates and actions from shared controls', async () => {
    const wrapper = mount(AuthEmailVerificationSection, {
      props: {
        email: 'old@example.com',
        code: '123',
        codeSent: true,
        emailLabel: 'Email',
        emailPlaceholder: 'email@example.com',
        codeLabel: 'Code',
        sendLabel: 'Send',
        resendLabel: 'Resend',
        verifyLabel: 'Verify',
      },
      global: {
        stubs: {
          BaseInput: BaseInputStub,
          BaseButton: BaseButtonStub,
          Mail: true,
          CheckCircle: true,
        },
      },
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new@example.com')
    await inputs[1].setValue('456')
    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.findAll('button')[1].trigger('click')

    expect(wrapper.emitted('update:email')?.[0]).toEqual(['new@example.com'])
    expect(wrapper.emitted('update:code')?.[0]).toEqual(['456'])
    expect(wrapper.emitted('send')).toHaveLength(1)
    expect(wrapper.emitted('verify')).toHaveLength(1)
    expect(wrapper.text()).toContain('Resend')
    expect(wrapper.text()).toContain('Verify')
  })

  it('stacks inline email verification controls on mobile', () => {
    const wrapper = mount(AuthEmailVerificationSection, {
      props: {
        email: '',
        code: '',
        layout: 'inline',
        emailLabel: 'Email',
        emailPlaceholder: 'email@example.com',
        codeLabel: 'Code',
        sendLabel: 'Send',
        resendLabel: 'Resend',
        verifyLabel: 'Verify',
      },
      global: {
        stubs: {
          BaseInput: BaseInputStub,
          BaseButton: BaseButtonStub,
          Mail: true,
        },
      },
    })

    const row = wrapper.get('.auth-email-verification-section > div')
    expect(row.classes()).toEqual(expect.arrayContaining(['flex-col', 'sm:flex-row']))
    expect(wrapper.get('button').classes()).toEqual(expect.arrayContaining(['w-full', 'sm:w-auto']))
  })
})
