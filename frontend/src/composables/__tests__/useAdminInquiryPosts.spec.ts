import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { adminApi } from '@/api/admin'
import {
  toAdminInquiryDetail,
  toAdminInquiryListItem,
  toAdminInquiryPage,
  useAdminInquiryPosts,
} from '../useAdminInquiryPosts'
import type { PageResponse, Post, PostSummary } from '@/types'

const mocks = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  queryOptions: [] as Array<Record<string, unknown>>,
}))

vi.mock('@/api/admin', () => ({
  adminApi: {
    getInquiryPosts: vi.fn(),
    getInquiryPost: vi.fn(),
  },
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn((options) => {
    mocks.queryOptions.push(options)
    return {
      data: ref(null),
      isLoading: ref(false),
      isFetching: ref(false),
      error: ref(null),
    }
  }),
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
}))

const postSummary = (overrides: Partial<PostSummary> = {}): PostSummary => ({
  postId: 7,
  title: 'Need help',
  viewCount: 1,
  likeCount: 0,
  commentCount: 2,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  author: {
    userId: 1,
    displayName: 'Admin User',
  },
  createdAt: '2026-05-26T01:02:03',
  inquiryAnswered: false,
  summary: '<p>Hello <strong>world</strong></p>',
  ...overrides,
})

const postDetail = (overrides: Partial<Post> = {}): Post => ({
  postId: 7,
  title: 'Need help',
  contents: '<p>Question body</p>',
  viewCount: 1,
  likeCount: 0,
  commentCount: 2,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  author: {
    userId: 1,
    displayName: 'Admin User',
  },
  board: {
    boardId: 1,
    boardName: 'Inquiry',
    boardUrl: 'inquiry',
  },
  createdAt: '2026-05-26T01:02:03',
  ...overrides,
})

const pageResponse = (content: PostSummary[]): PageResponse<PostSummary> => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: content.length === 0,
})

describe('useAdminInquiryPosts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queryOptions.length = 0
  })

  it('maps inquiry list DTOs to view-model fields', () => {
    const item = toAdminInquiryListItem(postSummary({
      summary: '<p>012345678901234567890123456789012345678901234567890123456789</p>',
      inquiryAnswered: true,
    }))

    expect(item).toMatchObject({
      id: 7,
      title: 'Need help',
      authorName: 'Admin User',
      statusLabelKey: 'admin.inquiries.status.answered',
    })
    expect(item.summaryText).toHaveLength(53)
    expect(item.statusClass).toContain('emerald')
  })

  it('maps inquiry detail DTOs to rendered content view-model fields', () => {
    const detail = toAdminInquiryDetail(postDetail())

    expect(detail).toMatchObject({
      id: 7,
      title: 'Need help',
      authorName: 'Admin User',
    })
    expect(detail.contentsHtml).toContain('Question body')
  })

  it('maps query results before exposing them to the view', async () => {
    vi.mocked(adminApi.getInquiryPosts).mockResolvedValueOnce({
      data: {
        success: true,
        data: pageResponse([postSummary({ authorName: 'Fallback Author', author: undefined as never })]),
      },
    } as never)
    vi.mocked(adminApi.getInquiryPost).mockResolvedValueOnce({
      data: {
        success: true,
        data: postDetail(),
      },
    } as never)

    const manager = useAdminInquiryPosts()
    const listQuery = mocks.queryOptions[0] as { queryFn: () => Promise<PageResponse<ReturnType<typeof toAdminInquiryListItem>>> }
    const detailQuery = mocks.queryOptions[1] as { queryFn: () => Promise<ReturnType<typeof toAdminInquiryDetail>> }

    await expect(listQuery.queryFn()).resolves.toMatchObject({
      content: [{
        id: 7,
        authorName: 'Fallback Author',
      }],
    })
    expect(adminApi.getInquiryPosts).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })

    manager.openDetail(7)
    await expect(detailQuery.queryFn()).resolves.toMatchObject({
      id: 7,
      authorName: 'Admin User',
    })
    expect(adminApi.getInquiryPost).toHaveBeenCalledWith(7)
  })

  it('preserves page metadata while mapping content', () => {
    const mappedPage = toAdminInquiryPage(pageResponse([postSummary()]))

    expect(mappedPage.totalElements).toBe(1)
    expect(mappedPage.content[0].id).toBe(7)
  })
})
