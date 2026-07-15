import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DraftList from '../DraftList.vue'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  deleteDraft: vi.fn(),
  cancelScheduledPost: vi.fn(),
  invalidateQueries: vi.fn(),
  addToast: vi.fn(),
  markDeleted: vi.fn(),
  draftRefetch: vi.fn(),
  scheduledRefetch: vi.fn(),
  listCall: 0,
}))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@tanstack/vue-query', () => ({ useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: mocks.confirm }) }))
vi.mock('@/composables/useUser', () => ({
  useUser: () => ({ useMyDrafts: vi.fn(), useMyScheduledPosts: vi.fn() }),
  userQueryKeys: { draftsRoot: ['user', 'drafts'], scheduledPostsRoot: ['user', 'scheduled'] },
}))
vi.mock('@/features/board/posts/queries/usePost', () => ({
  usePost: () => ({
    useDeleteDraft: () => ({ mutateAsync: mocks.deleteDraft, isPending: ref(false) }),
    useCancelScheduledPost: () => ({ mutateAsync: mocks.cancelScheduledPost, isPending: ref(false) }),
  }),
}))
vi.mock('@/features/board/posts/draft/postDraftTombstone', () => ({
  markDraftDeletedLocally: mocks.markDeleted,
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { userId: 7 } }) }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/composables/usePaginatedListState', () => ({
  usePaginatedListState: () => {
    mocks.listCall += 1
    if (mocks.listCall === 1) {
      return {
        page: ref(0), size: ref(15), handlePageChange: vi.fn(), handleSizeChange: vi.fn(),
        items: ref([
          { draftId: 1, title: ' Draft ', boardUrl: 'news', boardName: 'News', updatedAt: '2026-07-01', originalPostId: 3 },
          { draftId: 2, title: ' ', boardUrl: 'free', boardName: '', modifiedAt: '2026-07-02', originalPostId: null },
        ]),
        totalPages: ref(1), isLoading: ref(false), errorMessage: ref(''), refetch: mocks.draftRefetch,
      }
    }
    return {
      page: ref(0), size: ref(10), handlePageChange: vi.fn(), handleSizeChange: vi.fn(),
      items: ref([
        { scheduledPostId: 10, title: ' Scheduled ', boardUrl: 'news', boardName: 'News', scheduledAt: '2026-07-03', status: 'SCHEDULED', publishedPostId: null },
        { scheduledPostId: 11, title: '', boardUrl: 'free', boardName: '', scheduledAt: '2026-07-04', status: 'PUBLISHED', publishedPostId: 9 },
      ]),
      totalPages: ref(1), isLoading: ref(false), errorMessage: ref(''), refetch: mocks.scheduledRefetch,
    }
  },
}))

function mountList() {
  mocks.listCall = 0
  return mount(DraftList, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        PaginatedListCard: { template: '<section><slot /></section>' },
        BaseButton: {
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['disabled'],
          emits: ['click'],
        },
        BaseBadge: { template: '<span><slot /></span>' },
        RouterLink: { template: '<a><slot /></a>', props: ['to'] },
        Pencil: true, Trash2: true, ExternalLink: true, XCircle: true,
      },
    },
  })
}

describe('DraftList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.deleteDraft.mockResolvedValue(undefined)
    mocks.cancelScheduledPost.mockResolvedValue(undefined)
  })

  it('renders draft routes and performs delete and scheduled cancellation', async () => {
    const wrapper = mountList()
    expect(wrapper.text()).toContain('Draft')
    expect(wrapper.text()).toContain('user.draftList.untitled')

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await flushPromises()
    await buttons.at(-1)!.trigger('click')
    await flushPromises()

    expect(mocks.deleteDraft).toHaveBeenCalledWith(1)
    expect(mocks.markDeleted).toHaveBeenCalledWith(7, 1)
    expect(mocks.cancelScheduledPost).toHaveBeenCalledWith(10)
    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2)
    expect(mocks.draftRefetch).toHaveBeenCalled()
    expect(mocks.scheduledRefetch).toHaveBeenCalled()
  })

  it('handles declined confirmation and mutation failures', async () => {
    const wrapper = mountList()
    const buttons = wrapper.findAll('button')
    mocks.confirm.mockResolvedValueOnce(false)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(mocks.deleteDraft).not.toHaveBeenCalled()

    mocks.deleteDraft.mockRejectedValueOnce(new Error('delete failed'))
    mocks.cancelScheduledPost.mockRejectedValueOnce(new Error('cancel failed'))
    await buttons[0].trigger('click')
    await flushPromises()
    await buttons.at(-1)!.trigger('click')
    await flushPromises()
    expect(mocks.addToast).toHaveBeenCalledWith('user.draftList.deleteFailed', 'error')
    expect(mocks.addToast).toHaveBeenCalledWith('user.draftList.cancelScheduledFailed', 'error')
  })
})
