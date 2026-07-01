import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import PostListDesktopTable from '../PostListDesktopTable.vue'
import type { PostSummary } from '@/types'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/utils/date', () => ({
  formatRelativeDate: () => 'now',
}))

const columns: TableColumn[] = [
  { key: 'postId', label: 'No', sortable: true, align: 'center' },
  { key: 'boardName', label: 'Board', align: 'left' },
  { key: 'title', label: 'Title', align: 'left' },
  { key: 'author', label: 'Author', align: 'left' },
  { key: 'likeCount', label: 'Likes', align: 'center' },
  { key: 'viewCount', label: 'Views', align: 'right' },
  { key: 'createdAt', label: 'Date', align: 'center' },
]

const createPost = (overrides: Partial<PostSummary> = {}): PostSummary => ({
  postId: 10,
  rowNum: 3,
  title: 'Desktop post',
  createdAt: '2026-01-01T00:00:00',
  viewCount: 11,
  likeCount: 5,
  commentCount: 2,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  boardName: 'Free',
  author: {
    userId: 1,
    displayName: 'Author',
  },
  ...overrides,
})

function mountTable(overrides: Partial<InstanceType<typeof PostListDesktopTable>['$props']> = {}) {
  return mount(PostListDesktopTable, {
    props: {
      posts: [createPost()],
      loading: false,
      columns,
      activeSortKey: null,
      activeSortDirection: null,
      showNoticeBadge: true,
      showCommentCount: true,
      showPreviewIndicator: true,
      showSecretIndicator: true,
      maxAuthorNameLength: 10,
      getRowClass: () => 'post-list-row',
      shouldInterceptInquiry: () => false,
      hasBoardRouteTarget: () => true,
      getBoardLinkTarget: () => '/board/free',
      getTitleTag: () => 'span',
      getTitleProps: () => ({}),
      shouldShowInquiryStatus: () => false,
      hasInteractiveAuthor: () => false,
      getAuthorName: () => 'Author',
      getVisibleAuthorName: () => 'Author',
      isAgentAuthor: () => false,
      onNavigationClick: vi.fn(),
      ...overrides,
    },
    global: {
      stubs: {
        RouterLink: RouterLinkStub,
        UserMenu: true,
        ThumbsUp: true,
      },
    },
  })
}

describe('PostListDesktopTable', () => {
  it('renders desktop cell labels and emits sort keys', async () => {
    const wrapper = mountTable({
      posts: [createPost({ isNotice: true, boardName: '', likeCount: 8, viewCount: 13 })],
    })

    expect(wrapper.text()).toContain('common.notice')
    expect(wrapper.text()).toContain('-')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('13')

    await wrapper.get('.nv-base-table-header-button').trigger('click')

    expect(wrapper.emitted('sort')).toEqual([['postId']])
  })

  it('uses router links for board targets and fallback author labels', () => {
    const wrapper = mountTable({
      posts: [createPost({ boardName: 'General' })],
      getVisibleAuthorName: () => 'Fallback author',
    })

    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/board/free')
    expect(wrapper.text()).toContain('Fallback author')
  })
})
