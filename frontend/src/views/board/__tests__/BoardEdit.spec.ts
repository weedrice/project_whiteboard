import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref, watch, type PropType } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosHeaders, type AxiosResponse } from 'axios'
import BoardEdit from '../BoardEdit.vue'
import { boardApi } from '@/api/board'
import type { ApiResponse, BoardDetail } from '@/types'
import { getExposedVm } from '@/test/vue-test-helpers'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { BoardFormData } from '@/features/board/form/useBoardFormState'
import type { BoardFormSubmitAction } from '@/features/board/form/useBoardFormSubmit'

type BoardEditExposed = {
  confirmManagerSelection(users: Array<{ loginId: string; displayName?: string }>): Promise<void>
}

type BoardDetailResponse = AxiosResponse<ApiResponse<BoardDetail>>

const boardApiMock = vi.hoisted(() => ({
  getBoard: vi.fn<(boardUrl: string) => Promise<BoardDetailResponse>>(),
  getManagerAudits: vi.fn(),
}))

const authStoreMock = vi.hoisted(() => ({
  sessionGeneration: 0,
}))

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
  boardApi: boardApiMock,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreMock,
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

vi.mock('@/features/board/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: (boardUrl: { value: string }) => {
      const data = ref<BoardDetail | null>(null)
      const isLoading = ref(false)
      const error = ref<unknown>(null)

      watch(() => boardUrl.value, async (nextBoardUrl) => {
        if (!nextBoardUrl) return
        isLoading.value = true
        error.value = null
        try {
          const response = await boardApiMock.getBoard(nextBoardUrl)
          data.value = response.data.data
        } catch (err: unknown) {
          error.value = err
        } finally {
          isLoading.value = false
        }
      }, { immediate: true })

      return { data, isLoading, error }
    },
    useUpdateBoard: () => ({ mutateAsync: updateBoard }),
    useDeleteBoard: () => ({ mutateAsync: deleteBoard }),
    useTransferBoardManager: () => ({ mutateAsync: transferBoardManager }),
  }),
}))

const BoardFormStub = defineComponent({
  name: 'BoardForm',
  props: {
    initialData: {
      type: Object as PropType<BoardFormData>,
      required: true,
    },
    submitAction: {
      type: Function as PropType<BoardFormSubmitAction>,
      required: true,
    },
  },
  emits: ['submit', 'cancel'],
  setup(props) {
    return () =>
      h('button', {
        type: 'button',
        'data-testid': 'board-form',
        'data-board-url': props.initialData.boardUrl,
        onClick: () => {
          const controller = new AbortController()
          void props.submitAction(props.initialData, {
            signal: controller.signal,
            commitUpload: vi.fn(),
          })
        },
      }, props.initialData.boardName)
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
    authStoreMock.sessionGeneration = 0
    routerPush.mockReset()
    routerBack.mockReset()
    updateBoard.mockReset()
    deleteBoard.mockReset()
    transferBoardManager.mockReset()
    vi.mocked(boardApi.getBoard).mockReset()
    boardApiMock.getManagerAudits.mockReset()
    boardApiMock.getManagerAudits.mockResolvedValue({
      data: { success: true, data: { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, last: true } },
    })
  })

  afterEach(() => {
    mountedWrappers.forEach((wrapper) => wrapper.unmount())
    mountedWrappers.length = 0
  })

  function mockBoard(boardUrl: string, boardName = boardUrl): BoardDetailResponse {
    return {
      data: {
        success: true,
        data: {
          boardId: 1,
          boardName,
          boardUrl,
          description: '',
          iconUrl: '',
          sortOrder: 0,
          subscriberCount: 0,
          postCount: 0,
          allowNsfw: false,
          isPublic: true,
          isActive: true,
          isSubscribed: false,
          subscriptionAccessible: true,
          agentUseYn: false,
          guidePrompt: '',
          adminDisplayName: 'Manager',
          isAdmin: true,
          categories: [],
          latestPosts: [],
        },
      },
      status: 200,
      statusText: 'OK',
      headers: new AxiosHeaders(),
      config: {
        headers: new AxiosHeaders(),
      },
    }
  }

  async function mountBoardEdit() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(BoardEdit, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
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
      .mockResolvedValueOnce(mockBoard('free', 'Free Board'))
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board'))

    const wrapper = await mountBoardEdit()

    expect(boardApi.getBoard).toHaveBeenCalledWith('free')
    expect(wrapper.get('[data-testid="board-form"]').attributes('data-board-url')).toBe('free')
    expect(wrapper.getComponent({ name: 'UserSelectModal' }).props()).toMatchObject({
      isOpen: false,
      selectionMode: 'single',
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    routeState.params.boardUrl = 'qna'
    await nextTick()
    await flushPromises()

    expect(boardApi.getBoard).toHaveBeenLastCalledWith('qna')
    expect(wrapper.get('[data-testid="board-form"]').attributes('data-board-url')).toBe('qna')
    expect(wrapper.getComponent({ name: 'UserSelectModal' }).props('boardUrl')).toBe('qna')
  })

  it('submits updates with the current route boardUrl', async () => {
    vi.mocked(boardApi.getBoard)
      .mockResolvedValueOnce(mockBoard('free', 'Free Board'))
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board'))
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
      signal: expect.any(AbortSignal),
    })
  })

  it('aborts a submitAction update when the board route changes in place', async () => {
    vi.mocked(boardApi.getBoard)
      .mockResolvedValueOnce(mockBoard('free', 'Free Board'))
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board'))
    updateBoard.mockReturnValueOnce(new Promise(() => undefined))
    const wrapper = await mountBoardEdit()

    await wrapper.get('[data-testid="board-form"]').trigger('click')
    await vi.waitFor(() => expect(updateBoard).toHaveBeenCalledOnce())
    const signal = updateBoard.mock.calls[0][0].signal as AbortSignal

    routeState.params.boardUrl = 'qna'
    await nextTick()

    expect(signal.aborted).toBe(true)
  })

  it('shows a retryable error instead of an empty audit table when manager audits fail', async () => {
    vi.mocked(boardApi.getBoard).mockResolvedValueOnce(mockBoard('free', 'Free Board'))
    boardApiMock.getManagerAudits
      .mockRejectedValueOnce(new Error('audit failed'))
      .mockResolvedValueOnce({
        data: { success: true, data: { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, last: true } },
      })

    const wrapper = await mountBoardEdit()
    expect(wrapper.text()).toContain('common.messages.loadFailed')
    expect(boardApiMock.getManagerAudits.mock.calls[0][2]).toEqual({
      signal: expect.any(AbortSignal),
    })

    const retry = wrapper.findAll('button').find((button) => button.text().includes('common.error.retry'))
    expect(retry).toBeTruthy()
    await retry!.trigger('click')
    await flushPromises()
    expect(boardApiMock.getManagerAudits).toHaveBeenCalledTimes(2)
  })

  it('redirects before rendering the form when the current user is not a board manager', async () => {
    vi.mocked(boardApi.getBoard).mockResolvedValueOnce({
      ...mockBoard('free', 'Free Board'),
      data: {
        ...mockBoard('free', 'Free Board').data,
        data: {
          ...mockBoard('free', 'Free Board').data.data,
          isAdmin: false,
        },
      },
    })

    const wrapper = await mountBoardEdit()

    expect(routerPush).toHaveBeenCalledWith('/board/free')
    expect(wrapper.find('[data-testid="board-form"]').exists()).toBe(false)
  })

  it('ignores an in-flight board load after unmount', async () => {
    let resolveBoard: (value: ReturnType<typeof mockBoard>) => void = () => undefined
    vi.mocked(boardApi.getBoard).mockImplementationOnce(() => {
      return new Promise((resolve) => {
        resolveBoard = resolve
      })
    })

    const wrapper = mount(BoardEdit, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]],
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

    wrapper.unmount()

    resolveBoard({
      ...mockBoard('free', 'Free Board'),
      data: {
        ...mockBoard('free', 'Free Board').data,
        data: {
          ...mockBoard('free', 'Free Board').data.data,
          isAdmin: false,
        },
      },
    })
    await flushPromises()

    expect(routerPush).not.toHaveBeenCalled()
  })

  it('ignores stale manager transfer results after the route boardUrl changes', async () => {
    vi.mocked(boardApi.getBoard)
      .mockResolvedValueOnce(mockBoard('free', 'Free Board'))
      .mockResolvedValueOnce(mockBoard('qna', 'Q&A Board'))
    let resolveTransfer: (value: { adminDisplayName: string }) => void = () => undefined
    transferBoardManager.mockImplementationOnce(() => new Promise((resolve) => {
      resolveTransfer = resolve
    }))

    const wrapper = await mountBoardEdit()

    const transferPromise = getExposedVm<BoardEditExposed>(wrapper)
      .confirmManagerSelection([{ loginId: 'next-manager', displayName: 'Next Manager' }])

    routeState.params.boardUrl = 'qna'
    await nextTick()
    await flushPromises()

    resolveTransfer({ adminDisplayName: 'Old Manager' })
    await transferPromise
    await flushPromises()

    expect(transferBoardManager).toHaveBeenCalledWith({
      boardUrl: 'free',
      loginId: 'next-manager',
    })
    expect(wrapper.text()).not.toContain('Old Manager')
    expect(wrapper.text()).toContain('Manager')
  })
})
