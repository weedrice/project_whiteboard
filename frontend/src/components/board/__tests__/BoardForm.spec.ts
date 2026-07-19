import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import BoardForm from '../BoardForm.vue'
import { fileApi } from '@/api/file'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/file', () => ({
  fileApi: { uploadFile: vi.fn(), discardUploads: vi.fn() },
  resolveFileUploadUrl: (uploadedFile: { url?: string; fileUrl?: string }) => uploadedFile.url ?? uploadedFile.fileUrl ?? null,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { points: 1000 }, sessionGeneration: 0 }),
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
  props: {
    type: { type: String, default: 'button' },
    disabled: { type: Boolean, default: false },
  },
  setup(props, { slots }) {
    return () => h('button', { type: props.type, disabled: props.disabled }, slots.default?.())
  },
})

describe('BoardForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

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

  it('locks editable board fields while a save request is pending', () => {
    const wrapper = mount(BoardForm, {
      props: {
        isEdit: true,
        isSubmitting: true,
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

    expect(wrapper.get('fieldset').attributes()).toMatchObject({
      disabled: '',
      inert: 'true',
      'aria-busy': 'true',
    })
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    expect((wrapper.vm as unknown as { isSubmissionInProgress: () => boolean }).isSubmissionInProgress()).toBe(true)

    const unloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
    window.dispatchEvent(unloadEvent)
    expect(unloadEvent.defaultPrevented).toBe(true)
  })

  it('keeps board URL input to lowercase letters, numbers, underscores, and hyphens on create', async () => {
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
    expect(submit.boardUrl).toBe('free_board_123-')
  })

  it('clears selected icon file and preview when initial board data changes', async () => {
    const wrapper = mount(BoardForm, {
      props: {
        initialData: {
          boardName: 'Old Board',
          boardUrl: 'old',
          description: '',
          iconUrl: '/old.png',
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

    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    const fileInput = wrapper.get<HTMLInputElement>('#icon-upload')
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true })
    await fileInput.trigger('change')

    expect(wrapper.get('img').attributes('src')).toBe('blob:local-preview')

    await wrapper.setProps({
      initialData: {
        boardName: 'New Board',
        boardUrl: 'new',
        description: '',
        iconUrl: '',
        sortOrder: 0,
        allowNsfw: false,
        isPublic: true,
        agentUseYn: false,
        guidePrompt: '',
      },
    })
    await nextTick()

    expect(wrapper.find('img').exists()).toBe(false)

    await wrapper.find('form').trigger('submit.prevent')

    expect(fileApi.uploadFile).not.toHaveBeenCalled()
    const submit = wrapper.emitted('submit')?.[0]?.[0] as { boardUrl: string; iconUrl: string }
    expect(submit.boardUrl).toBe('new')
    expect(submit.iconUrl).toBe('')
  })

  it('aborts and discards an uploaded icon when the form is unmounted before save completes', async () => {
    vi.mocked(fileApi.uploadFile).mockResolvedValueOnce({
      data: {
        success: true,
        data: { fileId: 51, fileUrl: '/api/v1/files/51' },
      },
    } as Awaited<ReturnType<typeof fileApi.uploadFile>>)
    vi.mocked(fileApi.discardUploads).mockResolvedValue({
      data: { success: true, data: { discardedCount: 1 } },
    } as Awaited<ReturnType<typeof fileApi.discardUploads>>)
    let resolveSave!: (value: boolean) => void
    const submitAction = vi.fn().mockReturnValue(new Promise<boolean>((resolve) => {
      resolveSave = resolve
    }))
    const wrapper = mount(BoardForm, {
      props: { submitAction },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BaseInput: BaseInputStub,
          BaseTextarea: BaseTextareaStub,
          BaseCheckbox: BaseCheckboxStub,
          BaseButton: BaseButtonStub,
        },
      },
    })
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    const fileInput = wrapper.get<HTMLInputElement>('#icon-upload')
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true })
    await fileInput.trigger('change')

    const textInputs = wrapper.findAll('input')
      .filter((input) => input.attributes('type') !== 'file' && input.attributes('type') !== 'checkbox')
    await textInputs[0].setValue('Board')
    await textInputs[1].setValue('board')
    void wrapper.find('form').trigger('submit.prevent')
    await vi.waitFor(() => expect(submitAction).toHaveBeenCalledOnce())
    const signal = submitAction.mock.calls[0][1].signal as AbortSignal

    wrapper.unmount()
    resolveSave(false)
    await vi.waitFor(() => expect(fileApi.discardUploads).toHaveBeenCalledWith(
      [51],
      expect.any(Object),
    ))

    expect(signal.aborted).toBe(true)
  })

  it('tracks unsaved changes, blocks browser unload, and can mark the current snapshot saved', async () => {
    const wrapper = mount(BoardForm, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BaseInput: BaseInputStub,
          BaseTextarea: BaseTextareaStub,
          BaseCheckbox: BaseCheckboxStub,
          BaseButton: BaseButtonStub,
        },
      },
    })
    const exposed = wrapper.vm as unknown as {
      hasUnsavedChanges(): boolean
      markCurrentSnapshotSaved(): void
    }

    expect(exposed.hasUnsavedChanges()).toBe(false)
    const textInputs = wrapper.findAll('input')
      .filter((input) => input.attributes('type') !== 'file' && input.attributes('type') !== 'checkbox')
    await textInputs[0].setValue('Changed')
    expect(exposed.hasUnsavedChanges()).toBe(true)

    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)

    exposed.markCurrentSnapshotSaved()
    expect(exposed.hasUnsavedChanges()).toBe(false)
    wrapper.unmount()
  })
})
