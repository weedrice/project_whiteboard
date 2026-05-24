import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import BoardForm from '../BoardForm.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/file', () => ({
  fileApi: { uploadFile: vi.fn() },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { points: 1000 } }),
}))

vi.mock('@/stores/config', () => ({
  useConfigStore: () => ({ getConfig: vi.fn(() => '500') }),
}))

vi.mock('@/composables/useFormSubmit', () => ({
  useFormSubmit: () => ({
    isSubmitting: { value: false },
    submit: (callback: () => Promise<void>) => callback(),
  }),
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({ handleError: vi.fn() }),
}))

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: {
    modelValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        value: props.modelValue,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      })
  },
})

const BaseTextareaStub = defineComponent({
  name: 'BaseTextarea',
  props: {
    modelValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('textarea', {
        value: props.modelValue,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
      })
  },
})

const BaseCheckboxStub = defineComponent({
  name: 'BaseCheckbox',
  props: {
    id: { type: String, default: '' },
    modelValue: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        id: props.id,
        type: 'checkbox',
        checked: props.modelValue,
        disabled: props.disabled,
        onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).checked),
      })
  },
})

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  setup(_, { slots }) {
    return () => h('button', slots.default?.())
  },
})

describe('BoardForm', () => {
  it('keeps icon upload text inside the label and describes preview image', () => {
    const wrapper = mount(BoardForm, {
      props: {
        initialData: {
          boardName: 'Board',
          boardUrl: 'board',
          description: '',
          iconUrl: '/icon.png',
          sortOrder: 0,
          allowNsfw: false,
          isPublic: true,
          agentUseYn: false,
          guidePrompt: '',
        },
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseInput: BaseInputStub,
          BaseTextarea: BaseTextareaStub,
          BaseCheckbox: BaseCheckboxStub,
          BaseButton: BaseButtonStub,
        },
      },
    })

    const label = wrapper.get('label[for="icon-upload"]')
    expect(label.text()).toContain('board.form.iconImage')
    expect(wrapper.get('img').attributes('alt')).toBe('board.form.iconImage')
  })

  it('disables agent use when board is private', async () => {
    const wrapper = mount(BoardForm, {
      props: {
        initialData: {
          boardName: 'Board',
          boardUrl: 'board',
          description: '',
          iconUrl: '',
          sortOrder: 0,
          allowNsfw: false,
          isPublic: true,
          agentUseYn: true,
          guidePrompt: '',
        },
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseInput: BaseInputStub,
          BaseTextarea: BaseTextareaStub,
          BaseCheckbox: BaseCheckboxStub,
          BaseButton: BaseButtonStub,
        },
      },
    })

    await wrapper.find('#is-public').setValue(false)
    await nextTick()

    const agentCheckbox = wrapper.find<HTMLInputElement>('#agent-use-yn')
    expect(agentCheckbox.element.checked).toBe(false)
    expect(agentCheckbox.element.disabled).toBe(true)

    await wrapper.find('form').trigger('submit.prevent')

    const submit = wrapper.emitted('submit')?.[0]?.[0] as { agentUseYn: boolean }
    expect(submit.agentUseYn).toBe(false)
  })

  it('keeps board URL input to lowercase letters and underscores on create', async () => {
    const wrapper = mount(BoardForm, {
      props: {
        initialData: {
          boardName: 'Board',
          boardUrl: '',
          description: '',
          iconUrl: '',
          sortOrder: 0,
          allowNsfw: false,
          isPublic: true,
          agentUseYn: false,
          guidePrompt: '',
        },
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseInput: BaseInputStub,
          BaseTextarea: BaseTextareaStub,
          BaseCheckbox: BaseCheckboxStub,
          BaseButton: BaseButtonStub,
        },
      },
    })

    const textInputs = wrapper.findAll('input')
      .filter((input) => input.attributes('type') !== 'file' && input.attributes('type') !== 'checkbox')
    await textInputs[1].setValue('Free_BOARD_123-\uD55C\uAE00')
    await wrapper.find('form').trigger('submit.prevent')

    const submit = wrapper.emitted('submit')?.[0]?.[0] as { boardUrl: string }
    expect(submit.boardUrl).toBe('free_board_')
  })
})
