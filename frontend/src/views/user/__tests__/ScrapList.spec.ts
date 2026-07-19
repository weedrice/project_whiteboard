import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, reactive, ref } from 'vue'
import ScrapList from '../ScrapList.vue'
import { userApi } from '@/api/user'

const mocks = vi.hoisted(() => ({
  folderData: { __v_isRef: true, value: [] as Array<{ folderId: number; name: string }> },
  folderError: { __v_isRef: true, value: false },
  folderFetching: { __v_isRef: true, value: false },
  scrapItems: { __v_isRef: true, value: [] as Array<Record<string, unknown>> },
  refetchFolders: vi.fn(),
  refetchScraps: vi.fn(),
  invalidateQueries: vi.fn(),
  confirm: vi.fn(),
  resetPage: vi.fn(),
}))

const authState = vi.hoisted(() => ({
  sessionGeneration: 1,
  accessToken: 'token-a' as string | null,
  user: { userId: 1 } as { userId: number } | null,
}))
const reactiveAuthState = reactive(authState)

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => reactiveAuthState,
}))

vi.mock('@/features/user/useUser', () => ({
  useUser: () => ({
    useMyScraps: () => ({
      data: ref(null),
      isLoading: ref(false),
      isError: ref(false),
      refetch: mocks.refetchScraps,
    }),
  }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: () => ({
    data: mocks.folderData,
    isError: mocks.folderError,
    isFetching: mocks.folderFetching,
    refetch: mocks.refetchFolders,
  }),
}))

vi.mock('@/composables/usePaginatedQueryState', () => ({
  usePaginatedQueryState: () => ({
    page: ref(0),
    size: ref(15),
    params: ref({ page: 0, size: 15 }),
    handlePageChange: vi.fn(),
    handleSizeChange: vi.fn(),
    resetPage: mocks.resetPage,
  }),
  usePageResponseState: () => ({
    items: mocks.scrapItems,
    totalPages: ref(0),
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: mocks.confirm }),
}))

vi.mock('@tanstack/vue-query', () => ({
  QueryClient: class QueryClient {},
  QueryCache: class QueryCache {},
  MutationCache: class MutationCache {},
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getScrapFolders: vi.fn(),
    getMyScraps: vi.fn(),
    createScrapFolder: vi.fn(),
    updateScrapFolder: vi.fn(),
    deleteScrapFolder: vi.fn(),
    moveScrap: vi.fn(),
  },
}))

vi.mock('@/features/user/userQueryKeys', () => ({
  userQueryKeys: { scrapFolders: ['user', 'scrap-folders'] },
}))

vi.mock('@/utils/postNavigation', () => ({
  isInquiryPostItem: vi.fn(() => false),
  resolveBoardRoute: vi.fn(),
  resolvePostDetailRoute: vi.fn(),
}))

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: { disabled: Boolean },
  setup(props, { slots }) {
    return () => h('button', { disabled: props.disabled }, slots.default?.())
  },
})

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: { modelValue: { type: [String, Number], default: '' }, id: String },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('input', {
      id: props.id,
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    })
  },
})

function mountScrapList() {
  return mount(ScrapList, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        BaseButton: BaseButtonStub,
        BaseInput: BaseInputStub,
        PaginatedListCard: {
          template: '<main><slot name="header-actions" /><slot name="subheader" /><slot /></main>',
        },
        PostList: true,
        Bookmark: true,
        Check: true,
        Pencil: true,
        X: true,
      },
    },
  })
}

describe('ScrapList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.folderData.value = []
    mocks.folderError.value = false
    mocks.folderFetching.value = false
    mocks.scrapItems.value = []
    mocks.refetchFolders.mockResolvedValue(undefined)
    mocks.confirm.mockResolvedValue(true)
    authState.sessionGeneration = 1
    authState.accessToken = 'token-a'
    authState.user = { userId: 1 }
  })

  it('shows folder query failures separately and retries the folder query', async () => {
    mocks.folderError.value = true
    const wrapper = mountScrapList()

    const error = wrapper.get('[role="alert"]')
    expect(error.text()).toContain('common.messages.loadFailed')
    expect(wrapper.text()).not.toContain('user.scrapList.allFolder')

    await error.get('button').trigger('click')
    expect(mocks.refetchFolders).toHaveBeenCalledOnce()
  })

  it('contains count lookup failures inside the delete action error boundary', async () => {
    mocks.folderData.value = [{ folderId: 7, name: 'Saved' }]
    vi.mocked(userApi.getMyScraps).mockRejectedValueOnce(new Error('count failed'))
    const wrapper = mountScrapList()
    const deleteButton = wrapper.findAll('button')
      .find((button) => button.attributes('aria-label') === 'user.scrapList.deleteFolder')!

    await deleteButton.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('user.scrapList.folderActionFailed')
    expect(userApi.deleteScrapFolder).not.toHaveBeenCalled()
    expect(deleteButton.attributes('disabled')).toBeUndefined()
  })

  it('does not delete a same-numbered folder after the account changes during confirmation', async () => {
    mocks.folderData.value = [{ folderId: 7, name: 'Saved' }]
    vi.mocked(userApi.getMyScraps).mockResolvedValueOnce({ data: { data: { totalElements: 1 } } } as never)
    let resolveConfirm!: (value: boolean) => void
    mocks.confirm.mockImplementationOnce(() => new Promise((resolve) => { resolveConfirm = resolve }))
    const wrapper = mountScrapList()
    const deleteButton = wrapper.findAll('button')
      .find((button) => button.attributes('aria-label') === 'user.scrapList.deleteFolder')!

    await deleteButton.trigger('click')
    await flushPromises()
    authState.sessionGeneration = 2
    authState.accessToken = 'token-b'
    authState.user = { userId: 2 }
    resolveConfirm(true)
    await flushPromises()

    expect(userApi.deleteScrapFolder).not.toHaveBeenCalled()
  })

  it('resets account-bound filters and pagination when the session changes', async () => {
    mocks.folderData.value = [{ folderId: 7, name: 'Saved' }]
    const wrapper = mountScrapList()

    await wrapper.findAll('button').find((button) => button.text() === 'Saved')!.trigger('click')
    await wrapper.get('#scrap-search').setValue('previous account')
    await wrapper.findAll('form')[1].trigger('submit')
    expect(wrapper.text()).toContain('common.reset')

    reactiveAuthState.sessionGeneration = 2
    reactiveAuthState.accessToken = 'token-b'
    reactiveAuthState.user = { userId: 2 }
    await flushPromises()

    expect((wrapper.get('#scrap-search').element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).not.toContain('common.reset')
    expect(mocks.resetPage).toHaveBeenCalled()
    const allFolderButton = wrapper.findAll('button')
      .find((button) => button.text() === 'user.scrapList.allFolder')!
    expect(allFolderButton.classes()).toContain('nv-accent-bg')
  })

  it('moves a saved post to the selected folder and refreshes the session-scoped list', async () => {
    mocks.folderData.value = [{ folderId: 7, name: 'Saved' }]
    mocks.scrapItems.value = [{ postId: 11, title: 'Move me' }]
    vi.mocked(userApi.moveScrap).mockResolvedValueOnce({} as never)
    const wrapper = mountScrapList()

    await wrapper.get('#scrap-move-post').setValue('11')
    await wrapper.get('#scrap-move-folder').setValue('7')
    await wrapper.findAll('form').find((form) => form.find('#scrap-move-post').exists())!.trigger('submit')
    await flushPromises()

    expect(userApi.moveScrap).toHaveBeenCalledWith(11, { folderId: 7 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 1, 'user', 'scraps'],
    })
  })
})
