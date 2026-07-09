import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
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
  isActive: true,
  minWriteRole: 'USER',
  ...overrides,
})

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
    expect(wrapper.emitted('back-to-list')).toHaveLength(1)
    expect(wrapper.emitted('delete')).toHaveLength(1)
  })

  it('emits metadata updates from post form metadata panel', async () => {
    const wrapper = mount(PostFormMetadataPanel, {
      props: {
        layout: 'desktop',
        categories: [{ categoryId: 2, name: 'QnA' }],
        title: 'Post title',
        content: '<p>Body</p>',
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
