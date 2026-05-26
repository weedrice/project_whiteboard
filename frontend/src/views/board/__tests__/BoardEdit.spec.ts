import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BoardEdit from '../BoardEdit.vue'
import { boardApi } from '@/api/board'

const routeState = reactive({
  params: {
    boardUrl: 'free',
  },
})

const routerPush = vi.fn()
const routerBack = vi.fn()
const updateBoard = vi.fn()
const deleteBoard = vi.fn()
const transferBoardManager = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => routeState,
    useRouter: () => ({
      push: routerPush,
      back: routerBack,
    }),
  }
})

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/api/board', () => ({
  boardApi: {
    getBoard: vi.fn(),
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(true) }),
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

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useUpdateBoard: () => ({ mutateAsync: updateBoard }),
    useDeleteBoard: () => ({ mutateAsync: deleteBoard }),
    useTransferBoardManager: () => ({ mutateAsync: transferBoardManager }),
  }),
}))

const BoardFormStub = defineComponent({
  name: 'BoardForm',
  props: {
    initialData: {
      type: Object,
      required: true,
    },
  },
  emits: ['submit', 'cancel'],
  setup(props, { emit }) {
    return () =>
      h('button', {
        type: 'button',
        'data-testid': 'board-form',
        'data-board-url': (props.initialData as { boardUrl?: string }).boardUrl,
        onClick: () => emit('submit', props.initialData),
      }, (props.initialData as { boardName?: string }).boardName)
  },
})

const CategoryManagerStub = defineComponent({
  name: 'CategoryManager',
  props: {
    boardUrl: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    return () => h('div', { 'data-testid': 'category-manager' }, props.boardUrl)
  },
})

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { type: 'button', onClick: () => emit('click') }, slots.default?.())
  },
})

describe('BoardEdit', () => {
  const mountedWrappers: Array<ReturnType<typeof mount>> = []

  beforeEach(() => {
    routeState.params.boardUrl = 'free'
    routerPush.mockReset()
    routerBack.mockReset()
    updateBoard.mockReset()
    deleteBoard.mockReset()
    transferBoardManager.mockReset()
    vi.mocked(boardApi.getBoard).mockReset()
  })

  afterEach(() => {
    mountedWrappers.forEach((wrapper) => wrapper.unmount())
    mountedWrappers.length = 0
  })

  function mockBoard(boardUrl: string, boardName = boardUrl) {
    return {
      data: {
        success: true,
        data: {
          boardName,
          boardUrl,
          description: '',
          iconUrl: '',
          sortOrder: 0,
          allowNsfw: false,
          isPublic: true,
          agentUseYn: false,
          guidePrompt: '',
          adminDisplayName: 'Manager',
        },
      },
    }
  }

  async function mountBoardEdit() {
    const wrapper = mount(BoardEdit, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BoardForm: BoardFormStub,
          CategoryManager: CategoryManagerStub,
          BaseButton: BaseButtonStub,
          UserSelectModal: true,
        },
      },
    })
    mountedWrappers.push(wrapper)
    await flushPromises()
    return wrapper
  }

  it('reloads board state when the route boardUrl param changes', async () => {
    vi.mocked(boardApi.getBoard)
      .mockResolvedValueOnce(mockBoard('free', 'Free Board') as never)
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board') as never)

    const wrapper = await mountBoardEdit()

    expect(boardApi.getBoard).toHaveBeenCalledWith('free')
    expect(wrapper.get('[data-testid="board-form"]').attributes('data-board-url')).toBe('free')

    routeState.params.boardUrl = 'qna'
    await nextTick()
    await flushPromises()

    expect(boardApi.getBoard).toHaveBeenLastCalledWith('qna')
    expect(wrapper.get('[data-testid="board-form"]').attributes('data-board-url')).toBe('qna')
  })

  it('submits updates with the current route boardUrl', async () => {
    vi.mocked(boardApi.getBoard)
      .mockResolvedValueOnce(mockBoard('free', 'Free Board') as never)
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board') as never)
    updateBoard.mockResolvedValue({ boardUrl: 'qna' })

    const wrapper = await mountBoardEdit()

    routeState.params.boardUrl = 'qna'
    await nextTick()
    await flushPromises()
    await nextTick()

    await wrapper.get('[data-testid="board-form"]').trigger('click')

    expect(updateBoard).toHaveBeenCalledWith({
      boardUrl: 'qna',
      data: expect.objectContaining({ boardUrl: 'qna' }),
    })
  })
})
