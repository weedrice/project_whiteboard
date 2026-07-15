import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import BoardManagement from '../BoardManagement.vue'
import { BaseButtonStub, BaseModalStub, flushAll, getButtonByText, getExposedVm, identityT } from '@/test/vue-test-helpers'
import { createDeferred } from '@/test/async'

type BoardManagementExposed = {
  createForm: { boardName: string; boardUrl: string }
}

const mocks = vi.hoisted(() => ({
  createBoard: vi.fn(),
  updateBoard: vi.fn(),
  updateBoardManager: vi.fn(),
  addToast: vi.fn(),
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: identityT,
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

vi.mock('@/features/admin/useAdmin', () => ({
  useAdmin: () => ({
    useAdminBoards: () => ({
      data: ref([]),
      isLoading: ref(false),
      isError: ref(false),
      refetch: vi.fn(),
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
      isError: ref(false),
      refetch: vi.fn(),
    }),
    useUpdateBoardManager: () => ({
      mutateAsync: mocks.updateBoardManager,
    }),
  }),
}))

vi.mock('@/features/board/icons/useBoardIconUpload', () => ({
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

describe('BoardManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('passes the manager selection contract to UserSelectModal', () => {
    const wrapper = mount(BoardManagement, {
      global: {
        mocks: {
          $t: identityT,
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
          RouterLink: RouterLinkStub,
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
    const createBoardRequest = createDeferred<unknown>()
    mocks.createBoard.mockReturnValueOnce(createBoardRequest.promise)
    const wrapper = mount(BoardManagement, {
      global: {
        mocks: {
          $t: identityT,
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
          RouterLink: RouterLinkStub,
        },
      },
    })
    const vm = getExposedVm<BoardManagementExposed>(wrapper)

    await getButtonByText(wrapper, 'admin.boards.addTitle').trigger('click')
    vm.createForm.boardName = '  New board  '
    vm.createForm.boardUrl = 'new_board'
    await flushAll()

    await getButtonByText(wrapper.get('[data-test="modal"]'), 'common.save').trigger('click')
    await flushAll()

    const savingButton = getButtonByText(wrapper.get('[data-test="modal"]'), 'common.messages.saving')
    expect(savingButton?.attributes('disabled')).toBeDefined()
    expect(mocks.createBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardName: 'New board',
      boardUrl: 'new_board',
    }))

    createBoardRequest.resolve({})
    await flushAll()
  })
})
