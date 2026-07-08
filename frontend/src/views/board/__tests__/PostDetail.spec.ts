import { defineComponent, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import PostDetail from '../PostDetail.vue'

const {
  route,
  router,
  authState,
  postValue,
  deleteMutate,
  likeMutate,
  unlikeMutate,
  scrapMutate,
  unscrapMutate,
  reportMutate,
  toastAdd
} = vi.hoisted(() => ({
  route: {
    params: { postId: '15' },
    query: { page: '2' } as Record<string, string>,
    name: 'post-detail',
    path: '/board/free/post/15',
    hash: '',
    fullPath: '/board/free/post/15?page=2'
  },
  router: {
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn()
  },
  authState: {
    isAuthenticated: true,
    isAdmin: false,
    user: {
      userId: 7
    }
  },
  postValue: {
    postId: 15,
    title: '테스트 글',
    contents: '<h2>첫 섹션</h2><p>본문</p><h3>세부 항목</h3>',
    viewCount: 12,
    likeCount: 4,
    commentCount: 2,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    author: {
      userId: 7,
      displayName: '작성자',
      authorType: 'USER'
    },
    board: {
      boardId: 1,
      boardName: '자유스페이스',
      boardUrl: 'free',
      isAdmin: false
    },
    tags: ['vue'],
    liked: false,
    scrapped: false,
    seriesNavigation: null as null | {
      series: {
        seriesId: number
        title: string
        currentIndex?: number
        totalCount?: number
      }
      previousPost?: unknown
      nextPost?: unknown
    },
    boardListPage: undefined as number | undefined,
    createdAt: '2026-04-22T10:00:00',
    modifiedAt: '2026-04-22T10:00:00'
  },
  deleteMutate: vi.fn(),
  likeMutate: vi.fn(),
  unlikeMutate: vi.fn(),
  scrapMutate: vi.fn(),
  unscrapMutate: vi.fn(),
  reportMutate: vi.fn(),
  toastAdd: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => router
  }
})

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: toastAdd
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true)
  })
}))

vi.mock('@/features/board/posts/queries/usePost', () => ({
  usePost: () => ({
    usePostDetail: () => ({
      data: ref(postValue),
      isLoading: ref(false),
      error: ref(null)
    }),
    useDeletePost: () => ({ mutate: deleteMutate }),
    useLikePost: () => ({ mutate: likeMutate }),
    useUnlikePost: () => ({ mutate: unlikeMutate }),
    useScrapPost: () => ({ mutate: scrapMutate }),
    useUnscrapPost: () => ({ mutate: unscrapMutate }),
    useReportPost: () => ({ mutate: reportMutate })
  })
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn()
  }
}))

vi.mock('@/utils/sanitize', () => ({
  asSanitizedHtml: (html: string) => html,
  sanitizeQuillHtml: (html: string) => html
}))

vi.mock('@/utils/date', () => ({
  formatDate: () => '2026-04-22'
}))

vi.mock('@/utils/keyboard', () => ({
  isInputFocused: () => false
}))

vi.mock('@/utils/errorHandler', () => ({
  isRestrictedResourceError: () => false
}))

class MockIntersectionObserver {
  static callback: ((entries: Array<{ isIntersecting: boolean }>) => void) | null = null

  constructor(callback: (entries: Array<{ isIntersecting: boolean }>) => void) {
    MockIntersectionObserver.callback = callback
  }

  observe() {}
  disconnect() {}
}

const PostTagsStub = defineComponent({
  name: 'PostTags',
  props: {
    modelValue: {
      type: Array,
      default: () => []
    }
  },
  emits: ['tag-click'],
  template: '<button type="button" data-testid="tag" @click="$emit(\'tag-click\', modelValue[0])">#{{ modelValue[0] }}</button>'
})

describe('PostDetail', () => {
  beforeEach(() => {
    route.hash = ''
    router.push.mockReset()
    router.replace.mockReset()
    router.back.mockReset()
    toastAdd.mockReset()
    route.query = { page: '2' }
    route.fullPath = '/board/free/post/15?page=2'
    authState.isAuthenticated = true
    authState.isAdmin = false
    authState.user = { userId: 7 }

    postValue.postId = 15
    postValue.title = '테스트 글'
    postValue.contents = '<h2>첫 섹션</h2><p>본문</p><h3>세부 항목</h3>'
    postValue.viewCount = 12
    postValue.likeCount = 4
    postValue.commentCount = 2
    postValue.isSpoiler = false
    postValue.author = {
      userId: 7,
      displayName: '작성자',
      authorType: 'USER'
    }
    postValue.board = {
      boardId: 1,
      boardName: '자유스페이스',
      boardUrl: 'free',
      isAdmin: false
    }
    postValue.tags = ['vue']
    postValue.liked = false
    postValue.scrapped = false
    postValue.seriesNavigation = null
    postValue.boardListPage = undefined
    postValue.createdAt = '2026-04-22T10:00:00'
    postValue.modifiedAt = '2026-04-22T10:00:00'

    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)
    vi.stubGlobal('navigator', {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined)
      },
      share: vi.fn().mockResolvedValue(undefined)
    })
  })

  it('renders the floating board actions and header URL chip', async () => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 1440
    })

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    const urlChip = wrapper.find('.nv-post-url-chip')
    const boardActions = wrapper.find('.nv-post-board-actions')

    expect(urlChip.exists()).toBe(true)
    expect(boardActions.exists()).toBe(true)
    expect(boardActions.attributes('role')).toBe('navigation')
    expect(boardActions.attributes('aria-label')).toBe('board.postDetail.quickActions')
    expect(urlChip.text()).toContain('/board/free/post/15')
    await urlChip.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('/board/free/post/15?page=2'))
    expect(wrapper.text()).not.toContain('board.postDetail.reactions')
    expect(boardActions.find('[aria-label="board.postDetail.comments"]').exists()).toBe(true)
    expect(boardActions.find('[aria-label="board.postDetail.toList"]').exists()).toBe(true)
    expect(boardActions.find('[aria-label="board.postDetail.scrollTop"]').exists()).toBe(true)
    expect(wrapper.findAll('.nv-post-action-btn-circle')).toHaveLength(3)
    expect(wrapper.find('.nv-post-action-btn-circle[aria-label="common.likes"]').exists()).toBe(true)
    expect(wrapper.find('.nv-post-action-btn-circle[aria-label="board.postDetail.bookmark"]').exists()).toBe(true)
    expect(wrapper.find('.nv-post-action-btn-circle[aria-label="common.share"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('board.postDetail.tableOfContents')
  })

  it('renders editor html inside the rich content surface', async () => {
    postValue.contents = '<h2>제목 블록</h2><blockquote><p>인용문</p></blockquote><pre><code>const value = 1</code></pre>'

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    const content = wrapper.get('.nv-rich-content')

    expect(content.classes()).toContain('prose')
    expect(content.find('h2').text()).toBe('제목 블록')
    expect(content.find('blockquote p').text()).toBe('인용문')
    expect(content.find('pre code').text()).toBe('const value = 1')
  })

  it('shows a readable fallback when URL copy fails', async () => {
    vi.stubGlobal('navigator', {
      clipboard: {
        writeText: vi.fn().mockRejectedValue(new Error('copy failed'))
      },
      share: vi.fn().mockResolvedValue(undefined)
    })

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.find('.nv-post-url-chip').trigger('click')

    await vi.waitFor(() => {
      expect(toastAdd).toHaveBeenCalledWith('common.messages.processFailed', 'error')
    })
  })

  it('navigates back to the board route from the list action even when the list is already mounted', async () => {
    const mountedList = document.createElement('section')
    mountedList.id = 'board-post-list'
    document.body.appendChild(mountedList)

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    const listButton = wrapper.findAll('button').find((button) => button.attributes('aria-label') === 'board.postDetail.toList')
    await listButton?.trigger('click')

    expect(router.push).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        page: '2'
      }
    })

    wrapper.unmount()
    mountedList.remove()
  })

  it('shows author actions directly in the header instead of the overflow menu', async () => {
    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    expect(wrapper.find('.nv-post-header [aria-label="common.share"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="board.postDetail.moreActions"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('common.edit')
    expect(wrapper.text()).toContain('common.delete')
  })

  it('shows compact series metadata near the title', async () => {
    postValue.seriesNavigation = {
      series: {
        seriesId: 3,
        title: 'Series title',
        currentIndex: 3,
        totalCount: 7
      }
    }

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    expect(wrapper.text()).toContain('board.postDetail.seriesMeta')
  })

  it('uses the original board route after a delete succeeds', async () => {
    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.get('[aria-label="common.delete"]').trigger('click')

    await vi.waitFor(() => {
      expect(deleteMutate).toHaveBeenCalled()
    })

    const [, options] = deleteMutate.mock.calls[0]
    postValue.board.boardUrl = 'changed'
    options.onSuccess()

    expect(router.push).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        page: '2'
      }
    })
  })

  it('places the report action to the right of the share button for non-author users', async () => {
    authState.user = { userId: 99 }

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    const labels = wrapper
      .find('.nv-post-reaction-row')
      .findAll('button')
      .map((button) => button.attributes('aria-label'))

    expect(labels).toEqual([
      'common.likes',
      'board.postDetail.bookmark',
      'common.share',
      'common.report'
    ])
  })

  it('shows the mobile composer CTA when the composer leaves the viewport and scrolls back to it', async () => {
    vi.useFakeTimers()
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 390
    })

    const composerStub = {
      name: 'CommentList',
      template: '<div><div id="comment-composer"><textarea></textarea></div></div>'
    }

    const wrapper = mount(PostDetail, {
      attachTo: document.body,
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: composerStub,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.vm.$nextTick()

    const composer = document.getElementById('comment-composer') as HTMLDivElement
    const textarea = composer.querySelector('textarea') as HTMLTextAreaElement
    const scrollSpy = vi.fn()
    const focusSpy = vi.fn()

    composer.scrollIntoView = scrollSpy
    textarea.focus = focusSpy

    MockIntersectionObserver.callback?.([{ isIntersecting: false }])
    await wrapper.vm.$nextTick()

    const ctaButton = wrapper.findAll('button').find((button) => button.text().includes('board.postDetail.focusComposer'))
    expect(ctaButton?.exists()).toBe(true)

    await ctaButton?.trigger('click')
    vi.runAllTimers()

    expect(scrollSpy).toHaveBeenCalled()
    expect(focusSpy).toHaveBeenCalled()

    wrapper.unmount()
    vi.useRealTimers()
  })

  it('does not render the removed floating reaction bars', async () => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 1280
    })
    Object.defineProperty(window, 'scrollY', {
      configurable: true,
      value: 360
    })

    const wrapper = mount(PostDetail, {
      attachTo: document.body,
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    window.dispatchEvent(new Event('scroll'))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.nv-post-desktop-bar').exists()).toBe(false)
    expect(wrapper.find('.nv-post-mobile-bar').exists()).toBe(false)
  })

  it('syncs the board list page when opened from a direct post URL', async () => {
    route.query = {}
    route.fullPath = '/board/free/post/15'
    postValue.boardListPage = 3

    mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await vi.waitFor(() => {
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free/post/15',
      hash: '',
      query: { page: '4' }
      })
    })
  })

  it('ignores malformed hash values while mounting the post detail', async () => {
    route.hash = '#%'

    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: true,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.vm.$nextTick()

    expect(wrapper.find('.nv-rich-content').exists()).toBe(true)
  })

  it('handles tag search navigation in the parent view', async () => {
    route.query = { page: '2', categoryId: '3' }
    const wrapper = mount(PostDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentList: true,
          PostTags: PostTagsStub,
          UserMenu: true,
          BaseModal: true,
          ReportModal: true
        }
      }
    })

    await wrapper.get('[data-testid="tag"]').trigger('click')

    expect(router.push).toHaveBeenCalledWith({
      path: '/tag/vue'
    })
  })
})
