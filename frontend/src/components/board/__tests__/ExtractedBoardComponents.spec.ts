import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { nextTick } from 'vue'
import BoardNoticeList from '@/components/board/BoardNoticeList.vue'
import BoardDetailHeader from '@/components/board/BoardDetailHeader.vue'
import BoardPostFilters from '@/components/board/BoardPostFilters.vue'
import BoardPostSearch from '@/components/board/BoardPostSearch.vue'
import PostDetailHeader from '@/components/board/PostDetailHeader.vue'
import PostFormMetadataPanel from '@/components/board/PostFormMetadataPanel.vue'
import PostListMobileItem from '@/components/board/PostListMobileItem.vue'
import type { BoardDetail, Category, PostSummary } from '@/types/board'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/date', () => ({
  formatDate: () => '2026-06-29',
  formatRelativeDate: () => 'now',
}))

const postSummary = (overrides: Partial<PostSummary> = {}): PostSummary => ({
  postId: 1,
  title: 'Notice title',
  createdAt: '2026-06-29T00:00:00',
  viewCount: 3,
  likeCount: 2,
  commentCount: 1,
  isNotice: true,
  isNsfw: false,
  isSpoiler: false,
  author: {
    userId: 5,
    displayName: 'Author',
  },
  ...overrides,
})

const category = (overrides: Partial<Category> = {}): Category => ({
  categoryId: 1,
  name: 'General',
  sortOrder: 1,
  minWriteRole: 'USER',
  ...overrides,
})

function pointerEvent(
  type: string,
  {
    pointerId = 1,
    clientX = 0,
    clientY = 0,
    pointerType = 'mouse',
    button = 0,
    isPrimary = true,
  }: {
    pointerId?: number
    clientX?: number
    clientY?: number
    pointerType?: string
    button?: number
    isPrimary?: boolean
  } = {},
) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperties(event, {
    pointerId: { value: pointerId },
    clientX: { value: clientX },
    clientY: { value: clientY },
    pointerType: { value: pointerType },
    button: { value: button },
    isPrimary: { value: isPrimary },
  })
  return event
}

function dispatchMouseClick(element: Element) {
  element.dispatchEvent(new MouseEvent('click', {
    bubbles: true,
    cancelable: true,
    detail: 1,
  }))
}

const boardDetail = (overrides: Partial<BoardDetail> = {}): BoardDetail => ({
  boardId: 1,
  boardName: 'Free',
  boardUrl: 'free',
  description: 'Free board',
  iconUrl: '',
  sortOrder: 1,
  isAdmin: false,
  isSubscribed: false,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  subscriberCount: 3,
  postCount: 0,
  allowNsfw: false,
  categories: [],
  latestPosts: [],
  agentUseYn: false,
  ...overrides,
})

describe('extracted board components', () => {
  it('renders board detail header fallback icon, subscribe state and management links', async () => {
    const wrapper = mount(BoardDetailHeader, {
      props: {
        board: boardDetail({
          boardName: 'Free',
          boardUrl: 'free board',
          iconUrl: '',
          isAdmin: true,
          isSubscribed: true,
          adminUserId: 9,
          adminDisplayName: 'Manager',
        }),
        canWrite: true,
        isAuthenticated: true,
        isSubscribePending: true,
        buildBoardListRoute: () => '/board/free',
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          UserMenu: true,
          ShieldCheck: true,
          User: true,
        },
      },
    })

    const links = wrapper.findAllComponents(RouterLinkStub)
    const subscribeButton = wrapper.find('button.nv-board-subscribe-btn')

    expect(wrapper.find('.nv-board-icon-fallback').text()).toBe('F')
    expect(subscribeButton.attributes('disabled')).toBeDefined()
    expect(subscribeButton.attributes('aria-busy')).toBe('true')
    expect(subscribeButton.text()).toBeTruthy()
    expect(links.map((link) => link.props('to'))).toContain('/board/free%20board/edit')
    expect(links.map((link) => link.props('to'))).toContain('/board/free%20board/write')

    await subscribeButton.trigger('click')

    expect(wrapper.emitted('subscribe')).toBeUndefined()
  })

  it('connects board notice expand control with the notice list', async () => {
    const wrapper = mount(BoardNoticeList, {
      props: {
        notices: [postSummary({ postId: 1 }), postSummary({ postId: 2 })],
        visibleNotices: [postSummary({ postId: 1 })],
        hasNoticeOverflow: true,
        isExpanded: false,
        highlightedPostId: 1,
        getNoticeRoute: (notice) => `/board/free/post/${notice.postId}`,
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          Megaphone: true,
          ChevronDown: true,
          ChevronUp: true,
        },
      },
    })

    const button = wrapper.get('.nv-board-notice-more')
    const listId = button.attributes('aria-controls')

    expect(button.attributes('aria-expanded')).toBe('false')
    expect(listId).toBeTruthy()
    expect(wrapper.find(`#${listId}`).exists()).toBe(true)
    expect(wrapper.getComponent(RouterLinkStub).props('to')).toBe('/board/free/post/1')
    expect(wrapper.get('.nv-board-notice-heading').text()).toBe('board.detail.notices.title')
    expect(wrapper.find('.nv-board-notice-badge').exists()).toBe(false)

    await button.trigger('click')

    expect(wrapper.emitted('update:isExpanded')?.[0]).toEqual([true])
  })

  it('emits filter actions with category ids', async () => {
    const wrapper = mount(BoardPostFilters, {
      props: {
        categories: [category({ categoryId: 2, name: 'QnA' })],
        isAllPostsActive: true,
        conceptOnly: false,
        selectedCategoryId: null,
      },
    })

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await buttons[2].trigger('click')

    expect(buttons[0].attributes('aria-pressed')).toBe('true')
    expect(wrapper.emitted('activateAll')).toHaveLength(1)
    expect(wrapper.emitted('toggleConcept')).toHaveLength(1)
    expect(wrapper.emitted('toggleCategory')?.[0]).toEqual([2])
  })

  it('keeps density controls beside the horizontally scrollable category rail', async () => {
    const wrapper = mount(BoardPostFilters, {
      props: {
        categories: [category({ categoryId: 2, name: 'QnA' })],
        isAllPostsActive: true,
        conceptOnly: false,
        selectedCategoryId: null,
        density: 'default',
      },
    })

    const toolbar = wrapper.get('.nv-board-toolbar-sticky')
    const rail = toolbar.get('.nv-board-filter-rail')
    const densityControl = toolbar.get('.nv-board-density-control')

    expect(rail.element.parentElement).toBe(toolbar.element)
    expect(densityControl.element.parentElement).toBe(toolbar.element)

    const compactButton = densityControl.findAll('button').find((button) => button.text() === 'board.list.densityCompact')
    await compactButton?.trigger('click')

    expect(wrapper.emitted('update:density')?.[0]).toEqual(['compact'])
  })

  it('scrolls the category rail by mouse drag without activating the dragged chip', async () => {
    const wrapper = mount(BoardPostFilters, {
      props: {
        categories: [category({ categoryId: 2, name: 'QnA' })],
        isAllPostsActive: true,
        conceptOnly: false,
        selectedCategoryId: null,
      },
    })
    const rail = wrapper.get<HTMLElement>('.nv-board-filter-rail')
    const railElement = rail.element
    const setPointerCapture = vi.fn()
    const releasePointerCapture = vi.fn()
    Object.defineProperties(railElement, {
      clientWidth: { configurable: true, value: 200 },
      scrollWidth: { configurable: true, value: 600 },
      scrollLeft: { configurable: true, writable: true, value: 100 },
      setPointerCapture: { configurable: true, value: setPointerCapture },
      hasPointerCapture: { configurable: true, value: vi.fn(() => true) },
      releasePointerCapture: { configurable: true, value: releasePointerCapture },
    })

    const categoryButton = wrapper.findAll('button')[2]
    categoryButton.element.dispatchEvent(pointerEvent('pointerdown', { clientX: 300 }))
    railElement.dispatchEvent(pointerEvent('pointermove', { clientX: 240 }))
    await nextTick()

    expect(railElement.scrollLeft).toBe(160)
    expect(rail.classes()).toContain('is-dragging')

    railElement.dispatchEvent(pointerEvent('pointerup', { clientX: 240 }))
    dispatchMouseClick(categoryButton.element)
    await nextTick()

    expect(setPointerCapture).toHaveBeenCalledWith(1)
    expect(releasePointerCapture).toHaveBeenCalledWith(1)
    expect(rail.classes()).not.toContain('is-dragging')
    expect(wrapper.emitted('toggleCategory')).toBeUndefined()

    dispatchMouseClick(categoryButton.element)
    await nextTick()
    expect(wrapper.emitted('toggleCategory')?.[0]).toEqual([2])

    categoryButton.element.dispatchEvent(pointerEvent('pointerdown', { pointerId: 3, clientX: 300 }))
    railElement.dispatchEvent(pointerEvent('pointermove', { pointerId: 3, clientX: 240 }))
    railElement.dispatchEvent(pointerEvent('lostpointercapture', { pointerId: 3, clientX: 240 }))
    document.dispatchEvent(pointerEvent('pointerup', { pointerId: 3, clientX: 240 }))
    await new Promise((resolve) => window.setTimeout(resolve, 0))
    dispatchMouseClick(categoryButton.element)
    await nextTick()

    expect(wrapper.emitted('toggleCategory')).toHaveLength(2)
  })

  it('keeps ordinary clicks and native touch scrolling outside mouse drag handling', async () => {
    const wrapper = mount(BoardPostFilters, {
      props: {
        categories: [category({ categoryId: 2, name: 'QnA' })],
        isAllPostsActive: true,
        conceptOnly: false,
        selectedCategoryId: null,
      },
    })
    const railElement = wrapper.get<HTMLElement>('.nv-board-filter-rail').element
    const setPointerCapture = vi.fn()
    Object.defineProperties(railElement, {
      clientWidth: { configurable: true, value: 200 },
      scrollWidth: { configurable: true, value: 600 },
      scrollLeft: { configurable: true, writable: true, value: 25 },
      setPointerCapture: { configurable: true, value: setPointerCapture },
    })
    const categoryButton = wrapper.findAll('button')[2]

    const touchDown = pointerEvent('pointerdown', {
      clientX: 200,
      pointerType: 'touch',
    })
    const touchMove = pointerEvent('pointermove', {
      clientX: 100,
      pointerType: 'touch',
    })
    categoryButton.element.dispatchEvent(touchDown)
    railElement.dispatchEvent(touchMove)
    await categoryButton.trigger('click')

    expect(railElement.scrollLeft).toBe(25)
    expect(touchDown.defaultPrevented).toBe(false)
    expect(touchMove.defaultPrevented).toBe(false)
    expect(setPointerCapture).not.toHaveBeenCalled()
    expect(wrapper.emitted('toggleCategory')?.[0]).toEqual([2])
  })

  it('recovers safely when pointer capture is unavailable or unexpectedly lost', async () => {
    const wrapper = mount(BoardPostFilters, {
      props: {
        categories: [category({ categoryId: 2, name: 'QnA' })],
        isAllPostsActive: true,
        conceptOnly: false,
        selectedCategoryId: null,
      },
    })
    const rail = wrapper.get<HTMLElement>('.nv-board-filter-rail')
    const railElement = rail.element
    Object.defineProperties(railElement, {
      clientWidth: { configurable: true, value: 200 },
      scrollWidth: { configurable: true, value: 600 },
      scrollLeft: { configurable: true, writable: true, value: 100 },
    })
    const categoryButton = wrapper.findAll('button')[2]

    categoryButton.element.dispatchEvent(pointerEvent('pointerdown', { clientX: 300 }))
    railElement.dispatchEvent(pointerEvent('pointermove', { clientX: 240 }))
    railElement.dispatchEvent(pointerEvent('pointerleave', { clientX: 240 }))
    await nextTick()

    expect(rail.classes()).not.toContain('is-dragging')
    expect(railElement.scrollLeft).toBe(100)

    const setPointerCapture = vi.fn()
    Object.defineProperties(railElement, {
      setPointerCapture: { configurable: true, value: setPointerCapture },
      hasPointerCapture: { configurable: true, value: vi.fn(() => true) },
      releasePointerCapture: { configurable: true, value: vi.fn() },
    })
    categoryButton.element.dispatchEvent(pointerEvent('pointerdown', { pointerId: 2, clientX: 300 }))
    railElement.dispatchEvent(pointerEvent('pointermove', { pointerId: 2, clientX: 240 }))
    railElement.dispatchEvent(pointerEvent('lostpointercapture', { pointerId: 2, clientX: 240 }))
    dispatchMouseClick(categoryButton.element)
    await nextTick()

    expect(setPointerCapture).toHaveBeenCalledWith(2)
    expect(rail.classes()).not.toContain('is-dragging')
    expect(wrapper.emitted('toggleCategory')).toBeUndefined()

    await categoryButton.trigger('click')
    expect(wrapper.emitted('toggleCategory')?.[0]).toEqual([2])

    categoryButton.element.dispatchEvent(pointerEvent('pointerdown', { pointerId: 4, clientX: 300 }))
    railElement.dispatchEvent(pointerEvent('pointermove', { pointerId: 4, clientX: 240 }))
    railElement.dispatchEvent(pointerEvent('lostpointercapture', { pointerId: 4, clientX: 240 }))
    await categoryButton.trigger('click')

    expect(wrapper.emitted('toggleCategory')).toHaveLength(2)
  })

  it('emits search field updates and search commands', async () => {
    const wrapper = mount(BoardPostSearch, {
      props: {
        searchQuery: 'vue',
        searchType: 'TITLE_CONTENT',
        searchInputElementId: 'board-search-input',
        isSearching: true,
        canWrite: true,
        boardUrl: 'free',
        transientListError: 'temporary error',
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          Search: true,
          X: true,
        },
      },
    })

    await wrapper.get('#board-search-input').setValue('nuxt')
    await wrapper.get('#board-search-type').setValue('TITLE')
    await wrapper.findAll('button').find((button) => button.text() === 'search.doSearch')?.trigger('click')
    await wrapper.get('button[aria-label="board.detail.clearSearch"]').trigger('click')

    expect(wrapper.emitted('update:searchQuery')?.[0]).toEqual(['nuxt'])
    expect(wrapper.emitted('update:searchType')?.[0]).toEqual(['TITLE'])
    expect(wrapper.emitted('search')).toHaveLength(1)
    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.getComponent(RouterLinkStub).props('to')).toBe('/board/free/write')
    expect(wrapper.text()).toContain('temporary error')
  })

  it('renders post detail header actions and emits commands', async () => {
    const postView: PostDetailViewModel = {
      postId: 7,
      title: 'Post title',
      createdAt: '2026-06-29T00:00:00',
      editCount: 3,
      viewCount: 10,
      commentCount: 2,
      likeCount: 1,
      liked: false,
      scrapped: false,
      tags: [],
      boardName: 'Free',
      boardUrl: 'free',
      authorUserId: 3,
      authorDisplayName: 'Author',
      isBlinded: false,
      isBoardAdmin: true,
    }
    const wrapper = mount(PostDetailHeader, {
      props: {
        postView,
        editRoute: '/board/free/post/7/edit',
        isAgentAuthor: true,
        canEdit: true,
        canDelete: true,
        canManage: true,
        canViewHistory: true,
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          UserMenu: true,
          ArrowLeft: true,
          Clock: true,
          Eye: true,
          MessageSquare: true,
          Pencil: true,
          Trash2: true,
          User: true,
        },
      },
    })

    await wrapper.get('.nv-post-back-btn').trigger('click')
    await wrapper.get('[aria-label="common.delete"]').trigger('click')

    expect(wrapper.getComponent(RouterLinkStub).props('to')).toBe('/board/free/post/7/edit')
    expect(wrapper.text()).toContain('AGENT')
    expect(wrapper.get('[data-testid="post-edit-count"]').text()).toBe('board.postDetail.editedCount')
    expect(wrapper.emitted('back-to-list')).toHaveLength(1)
    expect(wrapper.emitted('delete')).toHaveLength(1)
    await wrapper.get('[data-testid="post-edit-count"]').trigger('click')
    expect(wrapper.emitted('show-history')).toHaveLength(1)

    await wrapper.setProps({ postView: { ...postView, editCount: 0 } })
    expect(wrapper.find('[data-testid="post-edit-count"]').exists()).toBe(true)
  })

  it('emits metadata updates from post form metadata panel', async () => {
    const wrapper = mount(PostFormMetadataPanel, {
      props: {
        layout: 'desktop',
        categories: [{ categoryId: 2, name: 'QnA' }],
        categoryId: '',
        seriesOptions: [],
        seriesId: '',
        newSeriesTitle: '',
        isCreatingSeries: false,
        tags: [],
        isNotice: false,
        isNsfw: false,
        isSpoiler: false,
        isSecret: false,
        showNotice: true,
        canShowNsfw: true,
      },
      global: {
        stubs: {
          PostTags: {
            props: ['modelValue', 'inputId'],
            emits: ['update:modelValue'],
            template: '<button type="button" data-testid="tags" @click="$emit(\'update:modelValue\', [\'vue\'])">tags</button>',
          },
        },
      },
    })

    await wrapper.get('#category').setValue('2')
    await wrapper.get('#isNotice').setValue(true)
    await wrapper.get('[data-testid="tags"]').trigger('click')

    expect(wrapper.emitted('update:categoryId')?.[0]).toEqual(['2'])
    expect(wrapper.emitted('update:isNotice')?.[0]).toEqual([true])
    expect(wrapper.emitted('update:tags')?.[0]).toEqual([['vue']])
  })

  it('renders mobile post items with route target and navigation event', async () => {
    const wrapper = mount(PostListMobileItem, {
      props: {
        post: postSummary({
          postId: 11,
          title: 'Mobile post',
          boardUrl: 'free',
          isNotice: false,
          author: { userId: 8, displayName: 'Agent', authorType: 'AGENT' },
        }),
        interactiveTag: 'router-link',
        postLink: '/board/free/post/11',
        isCurrent: true,
        showInquiryStatus: false,
        showNoticeBadge: true,
        showCommentCount: true,
        showPreviewIndicator: true,
        showSecretIndicator: true,
        deletedUserLabel: 'Deleted user',
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          Eye: true,
          MessageSquare: true,
          ThumbsUp: true,
          User: true,
          UserMenu: true,
        },
      },
    })

    await wrapper.getComponent(RouterLinkStub).trigger('click')

    expect(wrapper.getComponent(RouterLinkStub).props('to')).toBe('/board/free/post/11')
    expect(wrapper.getComponent(RouterLinkStub).attributes('aria-current')).toBe('page')
    expect(wrapper.text()).toContain('AGENT')
    expect(wrapper.emitted('navigate')?.[0]?.[1]).toMatchObject({ postId: 11 })
  })
})
