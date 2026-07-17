import { computed, defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardEditPage } from '@/features/board/edit/useBoardEditPage'

const mocks = vi.hoisted(() => ({
  authStore: { sessionGeneration: 0 },
  confirm: vi.fn(),
  deleteBoard: vi.fn(),
  updateBoard: vi.fn(),
  routerBack: vi.fn(),
  routerPush: vi.fn(),
  route: { params: { boardUrl: 'free' }, fullPath: '/board/free/edit' },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.routerPush, back: mocks.routerBack }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: vi.fn() }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: mocks.confirm }) }))
vi.mock('@/composables/useFormSubmit', () => ({
  useFormSubmit: () => ({ isSubmitting: ref(false), submit: (task: () => Promise<unknown>) => task() }),
}))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({ handleError: vi.fn() }) }))
vi.mock('@/features/board/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: () => ({ data: ref(null), isLoading: ref(false), error: ref(null) }),
    useUpdateBoard: () => ({ mutateAsync: mocks.updateBoard }),
    useDeleteBoard: () => ({ mutateAsync: mocks.deleteBoard }),
    useTransferBoardManager: () => ({ mutateAsync: vi.fn() }),
  }),
}))
vi.mock('@/features/board/edit/useBoardEditManagerAssignment', () => ({
  useBoardEditManagerAssignment: () => ({
    closeManagerModal: vi.fn(),
    confirmManagerSelection: vi.fn(),
    currentManagerLabel: computed(() => ''),
    isManagerModalOpen: ref(false),
    isTransferringManager: ref(false),
    openManagerModal: vi.fn(),
    resetManagerAssignmentState: vi.fn(),
    setCurrentManagerLabel: vi.fn(),
  }),
}))

function mountPage() {
  let page!: ReturnType<typeof useBoardEditPage>
  const wrapper = mount(defineComponent({
    setup() {
      page = useBoardEditPage()
      return () => null
    },
  }))
  return { page, wrapper }
}

describe('useBoardEditPage destructive intent', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.sessionGeneration = 0
    mocks.route.params.boardUrl = 'free'
    mocks.route.fullPath = '/board/free/edit'
  })

  it('does not delete after the session changes during confirmation', async () => {
    let resolveConfirmation!: (confirmed: boolean) => void
    mocks.confirm.mockReturnValueOnce(new Promise((resolve) => {
      resolveConfirmation = resolve
    }))
    const { page, wrapper } = mountPage()

    const deletion = page.handleDelete()
    mocks.authStore.sessionGeneration = 1
    resolveConfirmation(true)
    await deletion

    expect(mocks.deleteBoard).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not navigate after a deletion completes in a newer session', async () => {
    let resolveDeletion!: () => void
    mocks.confirm.mockResolvedValueOnce(true)
    mocks.deleteBoard.mockReturnValueOnce(new Promise<void>((resolve) => {
      resolveDeletion = resolve
    }))
    const { page, wrapper } = mountPage()

    const deletion = page.handleDelete()
    await Promise.resolve()
    mocks.authStore.sessionGeneration = 1
    resolveDeletion()
    await deletion

    expect(mocks.deleteBoard).toHaveBeenCalledWith('free')
    expect(mocks.routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('discards a delayed update after the session changes', async () => {
    let resolveUpdate!: (value: { boardUrl: string }) => void
    mocks.updateBoard.mockReturnValueOnce(new Promise((resolve) => {
      resolveUpdate = resolve
    }))
    const { page, wrapper } = mountPage()

    const update = page.handleUpdate({
      boardName: 'Free',
      boardUrl: 'free',
      description: '',
      iconUrl: '',
      sortOrder: 1,
      allowNsfw: false,
      isPublic: true,
      agentUseYn: false,
      guidePrompt: '',
    })
    await Promise.resolve()
    mocks.authStore.sessionGeneration = 1
    resolveUpdate({ boardUrl: 'free' })
    await update

    expect(mocks.updateBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardUrl: 'free',
      signal: expect.any(AbortSignal),
    }))
    expect(mocks.routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
