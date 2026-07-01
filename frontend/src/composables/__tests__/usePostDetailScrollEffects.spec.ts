import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { usePostDetailScrollEffects } from '@/composables/usePostDetailScrollEffects'
import { toPostDetailViewModel } from '@/composables/usePostDetailViewModel'
import { usePostDetailKeyboardShortcuts } from '@/composables/usePostDetailKeyboardShortcuts'
import type { Post } from '@/types'

vi.mock('@/composables/usePostDetailKeyboardShortcuts', () => ({
  usePostDetailKeyboardShortcuts: vi.fn(),
}))

function makePost(overrides: Partial<Post> = {}): Post {
  return {
    postId: 1,
    title: 'Post title',
    contents: '<p>content</p>',
    viewCount: 3,
    commentCount: 2,
    likeCount: 1,
    liked: false,
    scrapped: false,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    isSecret: false,
    createdAt: '2026-01-01T00:00:00Z',
    tags: [],
    board: {
      boardId: 1,
      boardName: 'Free',
      boardUrl: 'free',
    },
    author: {
      userId: 10,
      displayName: 'Author',
    },
    ...overrides,
  } as Post
}

function mountScrollEffects(options: {
  post?: Ref<Post | undefined>
  hash?: string
  routeName?: string
} = {}) {
  const post = options.post ?? ref<Post | undefined>(makePost())
  const route = reactive({
    hash: options.hash ?? '',
    name: options.routeName ?? 'post-detail',
  })
  const isBlurred = ref(false)
  const timeLeft = ref(0)
  const startBlurTimer = vi.fn()
  const clearBlurTimer = vi.fn()
  const markPostDetailUiMounted = vi.fn()
  const isPostDetailUiDisposed = vi.fn(() => false)
  const scheduleComposerFocus = vi.fn()
  const trackImageLoadTimeout = vi.fn((resolve: () => void) => resolve())
  const setupComposerObserver = vi.fn()
  const disposePostDetailUiEffects = vi.fn()
  const syncBoardListPageForDirectEntry = vi.fn()
  const buildEditRoute = vi.fn(() => '/board/free/post/1/edit')
  const goToList = vi.fn()
  const handleBookmark = vi.fn()
  const handleShare = vi.fn()
  const handleCopyUrl = vi.fn()
  const handleLike = vi.fn()
  const router = {
    push: vi.fn(),
    replace: vi.fn(),
  } as unknown as Router
  let exposed: ReturnType<typeof usePostDetailScrollEffects>

  const wrapper = mount(defineComponent({
    setup(_, { expose }) {
      const result = usePostDetailScrollEffects({
        route: route as unknown as RouteLocationNormalizedLoaded,
        router,
        post,
        postView: computed(() => (post.value ? toPostDetailViewModel(post.value) : null)),
        authStore: { isAuthenticated: true },
        canEdit: computed(() => true),
        isReportModalOpen: ref(false),
        isBlurred,
        timeLeft,
        startBlurTimer,
        clearBlurTimer,
        markPostDetailUiMounted,
        isPostDetailUiDisposed,
        scheduleComposerFocus,
        trackImageLoadTimeout,
        setupComposerObserver,
        disposePostDetailUiEffects,
        syncBoardListPageForDirectEntry,
        buildEditRoute,
        goToList,
        handleBookmark,
        handleShare,
        handleCopyUrl,
        handleLike,
      })

      exposed = result
      expose(result)
      return () => null
    },
  }))

  return {
    wrapper,
    exposed: exposed!,
    post,
    route,
    isBlurred,
    timeLeft,
    startBlurTimer,
    clearBlurTimer,
    markPostDetailUiMounted,
    setupComposerObserver,
    disposePostDetailUiEffects,
    syncBoardListPageForDirectEntry,
    scheduleComposerFocus,
  }
}

describe('usePostDetailScrollEffects', () => {
  beforeEach(() => {
    vi.mocked(usePostDetailKeyboardShortcuts).mockClear()
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('initializes spoiler blur state when a spoiler post is loaded', async () => {
    const state = mountScrollEffects({
      post: ref(makePost({ isSpoiler: true })),
    })
    await nextTick()

    expect(state.isBlurred.value).toBe(true)
    expect(state.timeLeft.value).toBe(5)
    expect(state.startBlurTimer).toHaveBeenCalledTimes(1)
    expect(state.syncBoardListPageForDirectEntry).toHaveBeenCalledTimes(1)
    expect(usePostDetailKeyboardShortcuts).toHaveBeenCalledTimes(1)
  })

  it('clears spoiler timer state when a normal post replaces a spoiler post', async () => {
    const post = ref<Post | undefined>(makePost({ isSpoiler: true }))
    const state = mountScrollEffects({ post })
    await nextTick()

    post.value = makePost({ postId: 2, isSpoiler: false })
    await nextTick()

    expect(state.isBlurred.value).toBe(false)
    expect(state.clearBlurTimer).toHaveBeenCalled()
  })

  it('scrolls to hash targets after route hash changes', async () => {
    const target = document.createElement('section')
    target.id = 'target'
    target.scrollIntoView = vi.fn()
    document.body.append(target)
    const state = mountScrollEffects()

    state.route.hash = '#target'
    await nextTick()
    await nextTick()

    expect(target.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth' })
  })

  it('falls back to the comments section when the composer is missing', () => {
    const state = mountScrollEffects()
    const comments = document.createElement('section')
    comments.getBoundingClientRect = vi.fn(() => ({
      top: 240,
      left: 0,
      right: 0,
      bottom: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 240,
      toJSON: () => ({}),
    }))
    state.exposed.commentsRef.value = comments

    state.exposed.scrollToCommentComposer()

    expect(window.scrollTo).toHaveBeenCalledWith({
      top: 144,
      behavior: 'smooth',
    })
    expect(state.scheduleComposerFocus).not.toHaveBeenCalled()
  })

  it('disposes UI effects on unmount', () => {
    const state = mountScrollEffects()

    state.wrapper.unmount()

    expect(state.disposePostDetailUiEffects).toHaveBeenCalledTimes(1)
  })
})
