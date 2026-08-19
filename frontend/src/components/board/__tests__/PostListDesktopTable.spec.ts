import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import PostListDesktopTable from '../PostListDesktopTable.vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import type { PostSummary } from '@/types'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/api', () => ({
  default: { get: mocks.get },
}))

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
      density: 'default',
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
        Teleport: true,
        RouterLink: RouterLinkStub,
        UserMenu: true,
        ThumbsUp: true,
      },
    },
  })
}

describe('PostListDesktopTable', () => {
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>
  let revokeObjectUrlSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mocks.get.mockReset()
    createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:thumbnail')
    revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    createObjectUrlSpy.mockRestore()
    revokeObjectUrlSpy.mockRestore()
  })

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
    wrapper.unmount()
  })

  it('uses router links for board targets and fallback author labels', () => {
    const wrapper = mountTable({
      posts: [createPost({ boardName: 'General' })],
      getVisibleAuthorName: () => 'Fallback author',
    })

    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/board/free')
    expect(wrapper.text()).toContain('Fallback author')
    wrapper.unmount()
  })

  it('shows a small thumbnail preview on hover only for unprotected posts', async () => {
    const wrapper = mountTable({
      posts: [createPost({ thumbnailUrl: '/thumbnail.jpg' })],
    })

    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')

    expect(wrapper.get('.nv-post-hover-preview img').attributes('src')).toBe('/thumbnail.jpg')

    window.dispatchEvent(new Event('scroll'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.nv-post-hover-preview').exists()).toBe(false)

    await wrapper.setProps({
      posts: [createPost({ thumbnailUrl: '/protected.jpg', isSpoiler: true })],
    })
    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')

    expect(wrapper.find('.nv-post-hover-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('loads local thumbnail variants through the authenticated API client', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['thumbnail'], { type: 'image/webp' }) })
    const wrapper = mountTable({
      posts: [createPost({ thumbnailUrl: '/api/v1/files/55/variants/thumbnail' })],
    })

    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')
    expect(wrapper.find('.nv-post-hover-preview').exists()).toBe(false)

    await vi.waitFor(() => {
      expect(mocks.get).toHaveBeenCalledWith('/files/55/variants/thumbnail', expect.objectContaining({
        responseType: 'blob',
        skipGlobalErrorHandler: true,
      }))
    })
    await flushPromises()
    expect(wrapper.get('.nv-post-hover-preview img').attributes('src')).toBe('blob:thumbnail')

    await wrapper.get('.nv-post-title-cell').trigger('mouseleave')
    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.nv-post-hover-preview img').attributes('src')).toBe('blob:thumbnail')
    expect(revokeObjectUrlSpy).not.toHaveBeenCalled()

    wrapper.unmount()
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:thumbnail')
  })

  it('clears cached thumbnails and aborts pending requests at an auth session boundary', async () => {
    let pendingSignal: AbortSignal | undefined
    let resolvePending!: (value: { data: Blob }) => void
    mocks.get
      .mockResolvedValueOnce({ data: new Blob(['first'], { type: 'image/webp' }) })
      .mockImplementationOnce((_path: string, config: { signal: AbortSignal }) => {
        pendingSignal = config.signal
        return new Promise((resolve) => {
          resolvePending = resolve
        })
      })
    const wrapper = mountTable({
      posts: [createPost({ thumbnailUrl: '/api/v1/files/55/variants/thumbnail' })],
    })

    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')
    await flushPromises()
    expect(wrapper.get('.nv-post-hover-preview img').attributes('src')).toBe('blob:thumbnail')

    notifyAuthSessionBoundary(1)
    await wrapper.vm.$nextTick()
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:thumbnail')
    expect(wrapper.find('.nv-post-hover-preview').exists()).toBe(false)

    await wrapper.get('.nv-post-title-cell').trigger('mouseenter')
    await vi.waitFor(() => expect(pendingSignal).toBeDefined())
    notifyAuthSessionBoundary(2)
    expect(pendingSignal?.aborted).toBe(true)

    resolvePending({ data: new Blob(['stale'], { type: 'image/webp' }) })
    await flushPromises()
    expect(wrapper.find('.nv-post-hover-preview').exists()).toBe(false)
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
