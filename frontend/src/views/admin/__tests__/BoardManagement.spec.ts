import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import BoardManagement from '../BoardManagement.vue'
import { getButtonByText } from '@/test/vue-test-helpers'

const mocks = vi.hoisted(() => ({
  createBoard: vi.fn(),
  updateBoard: vi.fn(),
  updateBoardManager: vi.fn(),
  addToast: vi.fn(),
}))

const makeDeferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })
  return { promise, resolve }
}

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true),
  }),
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useAdminBoards: () => ({
      data: ref([]),
      isLoading: ref(false),
    }),
    useCreateBoard: () => ({
      mutateAsync: mocks.createBoard,
    }),
    useUpdateBoard: () => ({
      mutateAsync: mocks.updateBoard,
    }),
    useBoardManager: () => ({
      data: ref(null),
      isLoading: ref(false),
    }),
    useUpdateBoardManager: () => ({
      mutateAsync: mocks.updateBoardManager,
    }),
  }),
}))

vi.mock('@/composables/useBoardIconUpload', () => ({
  useBoardIconUpload: () => ({
    fileInput: ref(null),
    handleFileUpload: vi.fn(),
    chooseIconFile: vi.fn(),
  }),
}))

vi.mock('vuedraggable', () => ({
  default: defineComponent({
    name: 'AdminDraggableStub',
    setup(_, { slots }) {
      return () => h('div', slots.default?.())
    },
  }),
}))

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    disabled: { type: Boolean, default: false },
    type: { type: String, default: 'button' },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', {
      type: props.type,
      disabled: props.disabled,
      onClick: () => emit('click'),
    }, slots.default?.())
  },
})

const BaseModalStub = defineComponent({
  name: 'BaseModal',
  props: {
    isOpen: { type: Boolean, default: false },
  },
  setup(props, { slots }) {
    return () => props.isOpen ? h('div', { class: 'modal-stub' }, slots.default?.()) : null
  },
})

describe('BoardManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('passes the manager selection contract to UserSelectModal', () => {
    const wrapper = mount(BoardManagement, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseButton: BaseButtonStub,
          BaseModal: BaseModalStub,
          BaseInput: true,
          BaseTextarea: true,
          BaseCheckbox: true,
          BaseSpinner: true,
          UserSelectModal: true,
          GripVertical: true,
        },
      },
    })

    expect(wrapper.getComponent({ name: 'UserSelectModal' }).props()).toMatchObject({
      isOpen: false,
      title: 'admin.boards.userSelectTitle',
      selectionMode: 'single',
    })
  })

  it('uses a create-only submitting state for the create modal', async () => {
    const createBoardRequest = makeDeferred<unknown>()
    mocks.createBoard.mockReturnValueOnce(createBoardRequest.promise)
    const wrapper = mount(BoardManagement, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseButton: BaseButtonStub,
          BaseModal: BaseModalStub,
          BaseInput: true,
          BaseTextarea: true,
          BaseCheckbox: true,
          BaseSpinner: true,
          UserSelectModal: true,
          GripVertical: true,
        },
      },
    })
    const vm = wrapper.vm as unknown as {
      createForm: { boardName: string; boardUrl: string }
    }

    await getButtonByText(wrapper, 'admin.boards.addTitle').trigger('click')
    vm.createForm.boardName = 'New board'
    vm.createForm.boardUrl = 'new_board'
    await flushPromises()

    await getButtonByText(wrapper.get('.modal-stub'), 'common.save').trigger('click')
    await flushPromises()

    const savingButton = getButtonByText(wrapper.get('.modal-stub'), 'common.messages.saving')
    expect(savingButton?.attributes('disabled')).toBeDefined()
    expect(mocks.createBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardName: 'New board',
      boardUrl: 'new_board',
    }))

    createBoardRequest.resolve({})
    await flushPromises()
  })
})
