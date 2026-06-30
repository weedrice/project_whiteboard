import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, ref } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { usePostDetailActions } from '../usePostDetailActions'
import type { Post } from '@/types'

enableAutoUnmount(afterEach)

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  confirm: vi.fn(),
  deleteMutate: vi.fn(),
  likeMutate: vi.fn(),
  reportMutate: vi.fn(),
  scrapMutate: vi.fn(),
  unlikeMutate: vi.fn(),
  unscrapMutate: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
  }),
}))

vi.mock('@/composables/usePost', () => ({
  usePost: () => ({
    useDeletePost: () => ({ mutate: mocks.deleteMutate }),
    useLikePost: () => ({ mutate: mocks.likeMutate }),
    useReportPost: () => ({ mutate: mocks.reportMutate }),
    useScrapPost: () => ({ mutate: mocks.scrapMutate }),
    useUnlikePost: () => ({ mutate: mocks.unlikeMutate }),
    useUnscrapPost: () => ({ mutate: mocks.unscrapMutate }),
  }),
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
  },
}))

const route = {
  params: {
    postId: '7',
  },
}

const router = {
  push: vi.fn(),
}

const postFactory = (overrides: Partial<Post> = {}): Post => ({
  postId: 7,
  title: 'Post',
  contents: '<p>Body</p>',
  viewCount: 1,
  likeCount: 0,
  commentCount: 0,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  liked: false,
  scrapped: false,
  author: {
    userId: 1,
    displayName: 'Author',
  },
  board: {
    boardId: 1,
    boardName: 'Free',
    boardUrl: 'free',
  },
  createdAt: '2026-01-01T00:00:00',
  ...overrides,
})

function createActions(overrides: {
  authenticated?: boolean
  canReport?: boolean
  post?: Post | null
} = {}) {
  const closeOverflowMenu = vi.fn()
  let actions!: ReturnType<typeof usePostDetailActions>

  mount(defineComponent({
    setup() {
      actions = usePostDetailActions({
        post: ref(overrides.post === undefined ? postFactory() : overrides.post),
        canReport: computed(() => overrides.canReport ?? true),
        authStore: { isAuthenticated: overrides.authenticated ?? true },
        route: route as unknown as RouteLocationNormalizedLoaded,
        router: router as Partial<Router> as Router,
        t: (key: string) => key,
        buildBoardListRoute: (boardUrl: string) => ({ name: 'board-detail', params: { boardUrl } }),
        closeOverflowMenu,
      })

      return () => h('div')
    },
  }))

  return {
    actions,
    closeOverflowMenu,
  }
}

describe('usePostDetailActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
  })

  it('confirms before deleting and navigates to the board after success', async () => {
    const { actions } = createActions()

    await actions.handleDelete()

    expect(mocks.confirm).toHaveBeenCalledWith('common.messages.confirmDelete')
    expect(mocks.deleteMutate).toHaveBeenCalledWith('7', expect.objectContaining({
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    }))

    const options = mocks.deleteMutate.mock.calls[0][1]
    options.onSuccess()

    expect(router.push).toHaveBeenCalledWith({ name: 'board-detail', params: { boardUrl: 'free' } })
  })

  it('skips delete when confirmation is cancelled', async () => {
    mocks.confirm.mockResolvedValueOnce(false)
    const { actions } = createActions()

    await actions.handleDelete()

    expect(mocks.deleteMutate).not.toHaveBeenCalled()
  })

  it('likes or unlikes depending on current post state', async () => {
    const { actions } = createActions({ post: postFactory({ liked: false }) })

    await actions.handleLike()

    expect(mocks.likeMutate).toHaveBeenCalledWith('7', expect.objectContaining({
      onError: expect.any(Function),
    }))
    expect(mocks.unlikeMutate).not.toHaveBeenCalled()

    const likedPostActions = createActions({ post: postFactory({ liked: true }) }).actions

    await likedPostActions.handleLike()

    expect(mocks.unlikeMutate).toHaveBeenCalledWith('7', expect.objectContaining({
      onError: expect.any(Function),
    }))
  })

  it('scraps or unscraps depending on current post state', async () => {
    const { actions } = createActions({ post: postFactory({ scrapped: false }) })

    await actions.handleBookmark()

    expect(mocks.scrapMutate).toHaveBeenCalledWith('7', expect.objectContaining({
      onError: expect.any(Function),
    }))
    expect(mocks.unscrapMutate).not.toHaveBeenCalled()

    const scrappedPostActions = createActions({ post: postFactory({ scrapped: true }) }).actions

    await scrappedPostActions.handleBookmark()

    expect(mocks.unscrapMutate).toHaveBeenCalledWith('7', expect.objectContaining({
      onError: expect.any(Function),
    }))
  })

  it('does not like or bookmark when unauthenticated or post is missing', async () => {
    const unauthenticated = createActions({ authenticated: false }).actions
    await unauthenticated.handleLike()
    await unauthenticated.handleBookmark()

    const missingPost = createActions({ post: null }).actions
    await missingPost.handleLike()
    await missingPost.handleBookmark()

    expect(mocks.likeMutate).not.toHaveBeenCalled()
    expect(mocks.scrapMutate).not.toHaveBeenCalled()
  })

  it('opens the report modal only when reporting is allowed', () => {
    const allowed = createActions({ canReport: true })

    allowed.actions.openReportModal()

    expect(allowed.actions.showReportModal.value).toBe(true)
    expect(allowed.closeOverflowMenu).toHaveBeenCalled()

    const blocked = createActions({ canReport: false })
    blocked.actions.openReportModal()

    expect(blocked.actions.showReportModal.value).toBe(false)
  })

  it('submits a report and handles success and failure callbacks', async () => {
    const { actions } = createActions()
    actions.showReportModal.value = true

    const successPromise = actions.submitReport('Spam')

    expect(mocks.reportMutate).toHaveBeenCalledWith({
      targetPostId: '7',
      reason: 'Spam',
    }, expect.objectContaining({
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    }))

    const options = mocks.reportMutate.mock.calls[0][1]
    options.onSuccess()
    await expect(successPromise).resolves.toBe(true)
    expect(mocks.addToast).toHaveBeenCalledWith('board.postDetail.reportSuccess', 'success')
    expect(actions.showReportModal.value).toBe(false)

    const failurePromise = actions.submitReport('Abuse')
    const failureOptions = mocks.reportMutate.mock.calls[1][1]
    failureOptions.onError(new Error('failed'))
    await expect(failurePromise).resolves.toBe(false)
    expect(mocks.addToast).toHaveBeenCalledWith('board.postDetail.reportFailed', 'error')
  })
})
