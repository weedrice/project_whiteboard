import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeedPost } from '@/types'

const state = vi.hoisted(() => {
  const mockRef = <T>(value: T) => ({ __v_isRef: true as const, value })
  return {
    posts: mockRef<FeedPost[]>([]),
    isLoading: mockRef(false),
    isError: mockRef(false),
    isFetching: mockRef(false),
    isFetchNextPageError: mockRef(false),
    onIntersection: null as null | ((isIntersecting: boolean) => void),
    isFetchingNextPage: mockRef(false),
    hasNextPage: mockRef(false),
    fetchNextPage: vi.fn(),
    refetch: vi.fn(),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: { userId: 42 },
  }),
}))

vi.mock('@/features/feed/usePersonalFeed', () => ({
  usePersonalFeed: () => state,
}))

vi.mock('@/composables/useIntersectionObserver', () => ({
  useIntersectionObserver: (callback: (isIntersecting: boolean) => void) => {
    state.onIntersection = callback
    return { targetRef: { __v_isRef: true, value: null } }
  },
}))

import HomePersonalFeed from '../HomePersonalFeed.vue'

const EmptyStateStub = defineComponent({
  setup(_, { slots }) {
    return () => h('div', { 'data-testid': 'empty-state' }, slots.action?.())
  },
})

const ErrorStateStub = defineComponent({
  emits: ['retry'],
  setup(_, { emit }) {
    return () => h('button', { 'data-testid': 'retry', onClick: () => emit('retry') }, 'retry')
  },
})

const BaseButtonStub = defineComponent({
  props: ['to', 'disabled'],
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', {
      'data-to': props.to,
      disabled: props.disabled,
      onClick: () => emit('click'),
    }, slots.default?.())
  },
})

const mountFeed = () => mount(HomePersonalFeed, {
  global: {
    mocks: { $t: (key: string) => key },
    stubs: {
      BaseButton: BaseButtonStub,
      BaseSpinner: true,
      EmptyState: EmptyStateStub,
      ErrorState: ErrorStateStub,
      HomePostCard: {
        props: ['post', 'variant'],
        template: '<div data-testid="post-card" :data-variant="variant">{{ post.postId }}</div>',
      },
    },
  },
})

describe('HomePersonalFeed', () => {
  beforeEach(() => {
    state.posts.value = []
    state.isLoading.value = false
    state.isError.value = false
    state.isFetching.value = false
    state.isFetchNextPageError.value = false
    state.onIntersection = null
    state.isFetchingNextPage.value = false
    state.hasNextPage.value = false
    state.fetchNextPage.mockReset()
    state.refetch.mockReset()
  })

  it('offers board discovery and subscription management for an empty feed', () => {
    const wrapper = mountFeed()

    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-to="/boards"]').exists()).toBe(true)
    expect(wrapper.find('[data-to="/mypage/subscriptions"]').exists()).toBe(true)
  })

  it('retries an initial feed error', async () => {
    state.isError.value = true
    const wrapper = mountFeed()

    await wrapper.get('[data-testid="retry"]').trigger('click')
    expect(state.refetch).toHaveBeenCalledOnce()
  })

  it('keeps loaded posts on next-page failure and retries only on request', async () => {
    state.posts.value = [{ postId: 7 } as FeedPost]
    state.isError.value = true
    state.isFetchNextPageError.value = true
    state.hasNextPage.value = true
    const wrapper = mountFeed()

    expect(wrapper.get('[data-testid="post-card"]').text()).toBe('7')
    expect(wrapper.get('[role="status"]').text()).toBe('home.personal.loadMoreFailed')
    expect(wrapper.find('[data-testid="retry"]').exists()).toBe(false)
    state.onIntersection?.(true)
    expect(state.fetchNextPage).not.toHaveBeenCalled()
    const retry = wrapper.findAll('button').find((button) => button.text() === 'common.error.retry')!
    await retry.trigger('click')
    expect(state.fetchNextPage).toHaveBeenCalledOnce()
    expect(state.refetch).not.toHaveBeenCalled()
  })

  it('keeps loaded posts on background-refresh failure and retries the refresh', async () => {
    state.posts.value = [{ postId: 7 } as FeedPost]
    state.isError.value = true
    const wrapper = mountFeed()

    expect(wrapper.get('[data-testid="post-card"]').text()).toBe('7')
    expect(wrapper.get('[role="status"]').text()).toBe('home.personal.refreshFailed')
    const retry = wrapper.findAll('button').find((button) => button.text() === 'common.error.retry')!
    await retry.trigger('click')
    expect(state.refetch).toHaveBeenCalledOnce()
    expect(state.fetchNextPage).not.toHaveBeenCalled()
  })

  it('does not start another page request while a fetch is pending', async () => {
    state.posts.value = [{ postId: 7 } as FeedPost]
    state.hasNextPage.value = true
    state.isFetching.value = true
    const wrapper = mountFeed()
    state.onIntersection?.(true)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(state.fetchNextPage).not.toHaveBeenCalled()
  })

  it('renders mapped posts and keeps a manual load-more fallback', async () => {
    state.posts.value = [{
      postId: 7,
      title: 'Post 7',
      viewCount: 1,
      likeCount: 0,
      commentCount: 0,
      isNotice: false,
      isNsfw: false,
      isSpoiler: false,
      author: { userId: 7, displayName: 'Author 7' },
      createdAt: '2026-07-13T00:00:00',
      boardUrl: 'free',
      boardName: 'Free',
      authorName: 'Author 7',
      liked: false,
      scrapped: false,
      subscribed: false,
    }]
    state.hasNextPage.value = true
    const wrapper = mountFeed()

    expect(wrapper.get('[data-testid="post-card"]').text()).toBe('7')
    expect(wrapper.get('[data-testid="post-card"]').attributes('data-variant')).toBe('personal')
    const loadMore = wrapper.findAll('button').find((button) => button.text() === 'home.personal.loadMore')
    await loadMore!.trigger('click')
    expect(state.fetchNextPage).toHaveBeenCalledOnce()
  })
})
