import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BoardCreate from '../BoardCreate.vue'

const mocks = vi.hoisted(() => ({
  authStore: { sessionGeneration: 0 },
  createBoard: vi.fn(),
  handleError: vi.fn(),
  route: { fullPath: '/boards/create' },
  routerBack: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => mocks.route,
  useRouter: () => ({ back: mocks.routerBack, push: mocks.routerPush }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('@/composables/useFormSubmit', () => ({
  useFormSubmit: () => ({ isSubmitting: false, submit: (task: () => Promise<unknown>) => task() }),
}))
vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({ handleError: mocks.handleError }),
}))
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(true) }),
}))
vi.mock('@/features/board/posts/form/usePostFormLeaveGuard', () => ({
  usePostFormLeaveGuard: vi.fn(),
}))
vi.mock('@/features/board/useBoard', () => ({
  useBoard: () => ({ useCreateBoard: () => ({ mutateAsync: mocks.createBoard }) }),
}))

const BoardFormStub = defineComponent({
  emits: ['submit'],
  setup(_props, { emit }) {
    return () => h('button', {
      'data-testid': 'submit',
      onClick: () => emit('submit', {
        boardName: 'Free',
        boardUrl: 'free',
        description: '',
        iconUrl: '',
        sortOrder: 1,
        allowNsfw: false,
        isPublic: true,
        agentUseYn: false,
        guidePrompt: '',
      }),
    })
  },
})

describe('BoardCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.sessionGeneration = 0
    mocks.route.fullPath = '/boards/create'
  })

  it('does not navigate when a delayed creation belongs to an older session', async () => {
    let resolveCreation!: (value: { boardUrl: string }) => void
    mocks.createBoard.mockReturnValueOnce(new Promise((resolve) => {
      resolveCreation = resolve
    }))
    const wrapper = mount(BoardCreate, {
      global: {
        stubs: {
          BoardForm: BoardFormStub,
          PageHeader: true,
        },
      },
    })

    await wrapper.get('[data-testid="submit"]').trigger('click')
    await vi.waitFor(() => expect(mocks.createBoard).toHaveBeenCalledOnce())
    mocks.authStore.sessionGeneration = 1
    resolveCreation({ boardUrl: 'free' })
    await Promise.resolve()

    expect(mocks.createBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardUrl: 'free',
      signal: expect.any(AbortSignal),
    }))
    expect(mocks.routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
