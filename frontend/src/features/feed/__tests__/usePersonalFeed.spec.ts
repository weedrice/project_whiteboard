import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import type { PersonalFeedPage, PostSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  options: null as Record<string, unknown> | null,
  data: { value: undefined as { pages: PersonalFeedPage[] } | undefined },
}))

vi.mock('@tanstack/vue-query', () => ({
  useInfiniteQuery: vi.fn((options: Record<string, unknown>) => {
    mocks.options = options
    return {
      data: mocks.data,
      isLoading: ref(false),
      isError: ref(false),
      isFetchingNextPage: ref(false),
      hasNextPage: ref(false),
      fetchNextPage: vi.fn(),
      refetch: vi.fn(),
    }
  }),
}))

const feedApiMock = vi.hoisted(() => ({
  getMyFeeds: vi.fn(),
}))

vi.mock('@/api/feed', () => ({ feedApi: feedApiMock }))

import { usePersonalFeed } from '../usePersonalFeed'

const makePost = (postId: number): PostSummary => ({
  postId,
  title: `Post ${postId}`,
  viewCount: 1,
  likeCount: 2,
  commentCount: 3,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  author: { userId: postId, displayName: `Author ${postId}` },
  createdAt: '2026-07-13T00:00:00',
  boardUrl: 'free',
  boardName: 'Free',
})

const makePage = (content: PersonalFeedPage['content'], number = 0, hasNext = false): PersonalFeedPage => ({
  content,
  number,
  size: 20,
  totalElements: content.length,
  totalPages: hasNext ? number + 2 : number + 1,
  first: number === 0,
  last: !hasNext,
  empty: content.length === 0,
})

describe('usePersonalFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.options = null
    mocks.data.value = undefined
  })

  it('keys and requests the infinite feed by authenticated user', async () => {
    feedApiMock.getMyFeeds.mockResolvedValue({
      data: { success: true, data: makePage([], 2) },
    })

    usePersonalFeed(ref(42), ref(true))
    const options = mocks.options!
    const queryKey = options.queryKey as ReturnType<typeof computed>
    const enabled = options.enabled as ReturnType<typeof computed>
    const signal = new AbortController().signal
    const result = await (options.queryFn as (context: { pageParam: number; signal: AbortSignal }) => Promise<PersonalFeedPage>)({
      pageParam: 2,
      signal,
    })

    expect(queryKey.value).toEqual(['feed', 'personal', 42])
    expect(enabled.value).toBe(true)
    expect(feedApiMock.getMyFeeds).toHaveBeenCalledWith(2, 20, { signal })
    expect(result.number).toBe(2)
    expect((options.getNextPageParam as (page: PersonalFeedPage) => number | undefined)(makePage([], 2, true))).toBe(3)
  })

  it('maps supported posts and skips unavailable feed content across pages', () => {
    const post = makePost(7)
    mocks.data.value = {
      pages: [makePage([
        {
          feedId: 1,
          feedType: 'SUBSCRIPTION',
          contentType: 'POST',
          contentId: 7,
          isRead: false,
          createdAt: '2026-07-13T00:00:00',
          post,
        },
        {
          feedId: 2,
          feedType: 'FUTURE',
          contentType: 'COMMENT',
          contentId: 8,
          isRead: false,
          createdAt: '2026-07-13T00:00:00',
          post: null,
        },
      ])],
    }

    const { posts } = usePersonalFeed(ref(42), ref(true))

    expect(posts.value).toHaveLength(1)
    expect(posts.value[0]).toMatchObject({ postId: 7, authorName: 'Author 7', liked: false })
  })
})
