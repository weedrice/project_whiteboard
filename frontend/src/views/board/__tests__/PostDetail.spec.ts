import { ref } from 'vue'
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
  reportMutate
} = vi.hoisted(() => ({
  route: {
    params: { postId: '15' },
    query: { page: '2' },
    name: 'post-detail',
    path: '/board/free/post/15',
    hash: '',
    fullPath: '/board/free/post/15?page=2'
  },
  router: {
    push: vi.fn(),
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
      boardName: '자유게시판',
      boardUrl: 'free',
      isAdmin: false
    },
    tags: ['vue'],
    liked: false,
    scrapped: false,
    createdAt: '2026-04-22T10:00:00',
    modifiedAt: '2026-04-22T10:00:00'
  },
  deleteMutate: vi.fn(),
  likeMutate: vi.fn(),
  unlikeMutate: vi.fn(),
  scrapMutate: vi.fn(),
  unscrapMutate: vi.fn(),
  reportMutate: vi.fn()
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
    addToast: vi.fn()
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true)
  })
}))

vi.mock('@/composables/usePost', () => ({
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

describe('PostDetail', () => {
  beforeEach(() => {
    route.hash = ''
    router.push.mockReset()
    router.back.mockReset()
    authState.isAuthenticated = true
    authState.isAdmin = false
    authState.user = { userId: 7 }

    postValue.postId = 15
    postValue.title = '?뚯뒪??湲'
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
      boardName: '자유게시판',
      boardUrl: 'free',
      isAdmin: false
    }
    postValue.tags = ['vue']
    postValue.liked = false
    postValue.scrapped = false
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

  it('renders the TOC and bookmark label from the redesigned layout', async () => {
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
          BaseModal: true
        }
      }
    })

    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('board.postDetail.bookmark')
    expect(wrapper.text()).toContain('첫 섹션')
    expect(wrapper.text()).toContain('세부 항목')
  })

  it('moves author actions into the overflow menu', async () => {
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
          BaseModal: true
        }
      }
    })

    expect(wrapper.text()).not.toContain('common.delete')

    const moreButton = wrapper.findAll('button').find((button) => button.attributes('aria-label') === 'board.postDetail.moreActions')
    await moreButton?.trigger('click')

    expect(wrapper.text()).toContain('common.edit')
    expect(wrapper.text()).toContain('common.delete')
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
          BaseModal: true
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

  it('reveals the desktop sticky reaction bar after scrolling', async () => {
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
          BaseModal: true
        }
      }
    })

    window.dispatchEvent(new Event('scroll'))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.nv-post-desktop-bar').exists()).toBe(true)
  })
})
