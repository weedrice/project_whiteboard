import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ScrapList from '../ScrapList.vue'
import { userApi } from '@/api/user'

const mocks = vi.hoisted(() => ({
  folderData: { __v_isRef: true, value: [] as Array<{ folderId: number; name: string }> },
  folderError: { __v_isRef: true, value: false },
  folderFetching: { __v_isRef: true, value: false },
  refetchFolders: vi.fn(),
  refetchScraps: vi.fn(),
  invalidateQueries: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ sessionGeneration: 0 }),
}))

vi.mock('@/composables/useUser', () => ({
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
    resetPage: vi.fn(),
  }),
  usePageResponseState: () => ({
    items: ref([]),
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
  },
}))

vi.mock('@/composables/userQueryKeys', () => ({
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

function mountScrapList() {
  return mount(ScrapList, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        BaseButton: BaseButtonStub,
        BaseInput: {
          props: ['modelValue', 'id'],
          template: '<input :id="id" :value="modelValue" />',
        },
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
    mocks.refetchFolders.mockResolvedValue(undefined)
    mocks.confirm.mockResolvedValue(true)
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
})
