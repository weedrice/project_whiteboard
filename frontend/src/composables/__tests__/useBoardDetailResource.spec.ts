import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useBoardDetailResource } from '../useBoardDetailResource'

const mocks = vi.hoisted(() => ({
  board: { value: null as unknown },
  isBoardLoading: { value: false },
  boardError: { value: null as unknown },
  postsData: { value: null as unknown },
  isPostsLoading: { value: false },
  isPostsFetching: { value: false },
  postsError: { value: null as unknown },
  noticesData: { value: [] as unknown[] },
  subscribeMutate: vi.fn(),
  isSubscribePending: { value: false },
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: () => ({
      data: mocks.board,
      isLoading: mocks.isBoardLoading,
      error: mocks.boardError,
    }),
    useBoardPosts: () => ({
      data: mocks.postsData,
      isLoading: mocks.isPostsLoading,
      isFetching: mocks.isPostsFetching,
      error: mocks.postsError,
    }),
    useBoardNotices: () => ({
      data: mocks.noticesData,
    }),
    useSubscribeBoard: () => ({
      mutate: mocks.subscribeMutate,
      isPending: mocks.isSubscribePending,
    }),
  }),
}))

describe('useBoardDetailResource', () => {
  beforeEach(() => {
    mocks.board = ref(null)
    mocks.isBoardLoading = ref(false)
    mocks.boardError = ref(null)
    mocks.postsData = ref(null)
    mocks.isPostsLoading = ref(false)
    mocks.isPostsFetching = ref(false)
    mocks.postsError = ref(null)
    mocks.noticesData = ref([])
    mocks.isSubscribePending = ref(false)
    mocks.subscribeMutate.mockClear()
  })

  const createResource = () => useBoardDetailResource({
    boardUrl: ref('free'),
    queryParams: ref({ page: 0, size: 20 }),
    isSearching: ref(false),
    t: (key: string) => key,
  })

  it('sorts notices newest first and limits preview until expanded', () => {
    mocks.noticesData.value = [
      { postId: 1, title: 'old', createdAt: '2026-01-01T00:00:00.000Z' },
      { postId: 2, title: 'same-old-id', createdAt: '2026-01-03T00:00:00.000Z' },
      { postId: 4, title: 'same-new-id', createdAt: '2026-01-03T00:00:00.000Z' },
      { postId: 3, title: 'middle', createdAt: '2026-01-02T00:00:00.000Z' },
    ]

    const resource = createResource()

    expect(resource.notices.value.map((notice) => notice.postId)).toEqual([4, 2, 3, 1])
    expect(resource.visibleNotices.value.map((notice) => notice.postId)).toEqual([4, 2, 3])
    expect(resource.hasNoticeOverflow.value).toBe(true)

    resource.isNoticesExpanded.value = true
    expect(resource.visibleNotices.value.map((notice) => notice.postId)).toEqual([4, 2, 3, 1])

    resource.resetNoticeState()
    expect(resource.isNoticesExpanded.value).toBe(false)
  })

  it('uses localized fallback title and restricted blocking error', () => {
    mocks.boardError.value = {
      isAxiosError: true,
      response: { status: 403 },
    }

    const resource = useBoardDetailResource({
      boardUrl: ref(''),
      queryParams: ref({ page: 0, size: 20 }),
      isSearching: ref(false),
      t: (key: string) => key,
    })

    expect(resource.boardTitle.value).toBe('board.seo.spaceTitleFallback')
    expect(resource.blockingError.value).toBe('board.detail.restricted')
  })

  it('keeps populated posts visible while exposing transient list errors', () => {
    mocks.postsData.value = {
      content: [{ postId: 11, title: 'Existing' }],
      totalPages: 2,
      number: 0,
    }
    mocks.postsError.value = new Error('later page failed')

    const resource = createResource()

    expect(resource.posts.value).toEqual([{ postId: 11, title: 'Existing' }])
    expect(resource.blockingError.value).toBe('')
    expect(resource.transientListError.value).toBe('board.loadFailed')
  })

  it('shows list loading only while fetching a stale list key', async () => {
    const queryParams = ref({ page: 0, size: 20 })
    const resource = useBoardDetailResource({
      boardUrl: ref('free'),
      queryParams,
      isSearching: ref(false),
      t: (key: string) => key,
    })

    expect(resource.showPostListLoading.value).toBe(false)

    queryParams.value = { page: 1, size: 20 }
    mocks.isPostsFetching.value = true
    await nextTick()

    expect(resource.showPostListLoading.value).toBe(true)

    mocks.isPostsFetching.value = false
    await nextTick()

    expect(resource.showPostListLoading.value).toBe(false)
  })
})
