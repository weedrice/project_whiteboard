import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, type Ref } from 'vue'
import { createPaginationStub } from '@/test/vue-test-helpers'
import RecentViewed from '../RecentViewed.vue'

type RecentParams = { page: number; size: number }

const recentMock = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })

  return {
    latestParams: undefined as Ref<RecentParams> | undefined,
    queryState: {
      data: refOf(null as unknown),
      isLoading: refOf(false),
      isError: refOf(false),
      error: refOf(null as Error | null),
      refetch: vi.fn(),
    },
  }
})

vi.mock('@/composables/useUser', () => ({
  useUser: () => ({
    useRecentlyViewedPosts: (params: Ref<RecentParams>) => {
      recentMock.latestParams = params
      return recentMock.queryState
    },
  }),
}))

vi.mock('@/utils/postNavigation', () => ({
  isInquiryPostItem: vi.fn(() => false),
  resolveBoardRoute: vi.fn(() => ({ name: 'board-detail' })),
  resolvePostDetailRoute: vi.fn(() => ({ name: 'post-detail' })),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const PageSizeSelectorStub = defineComponent({
  name: 'PageSizeSelectorStub',
  props: {
    modelValue: {
      type: Number,
      required: true,
    },
  },
  emits: ['update:modelValue'],
  template: '<button data-test="size-change" @click="$emit(\'update:modelValue\', 30)">size {{ modelValue }}</button>',
})

const PaginationStub = createPaginationStub()

const EmptyStateStub = defineComponent({
  name: 'EmptyState',
  props: {
    title: {
      type: String,
      required: true,
    },
  },
  template: '<div data-test="empty-state">{{ title }}</div>',
})

const ErrorStateStub = defineComponent({
  name: 'ErrorState',
  props: {
    message: {
      type: String,
      required: true,
    },
    showRetry: Boolean,
  },
  emits: ['retry'],
  template: '<div data-test="error-state">{{ message }}<button v-if="showRetry" @click="$emit(\'retry\')">retry</button></div>',
})

const PostListStub = defineComponent({
  name: 'PostList',
  props: {
    posts: {
      type: Array,
      required: true,
    },
    showBoardName: Boolean,
    hideNoColumn: Boolean,
    resolvePostRoute: Function,
    resolveBoardRoute: Function,
    showInquiryStatus: Function,
  },
  template: `
    <div data-test="post-list">
      <article v-for="post in posts" :key="post.postId">{{ post.title }}</article>
      <span data-test="board-name">{{ showBoardName }}</span>
      <span data-test="hide-no-column">{{ hideNoColumn }}</span>
    </div>
  `,
})

const mountView = () => mount(RecentViewed, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      Clock: true,
      EmptyState: EmptyStateStub,
      ErrorState: ErrorStateStub,
      PageSizeSelector: PageSizeSelectorStub,
      Pagination: PaginationStub,
      PostList: PostListStub,
    },
  },
})

describe('RecentViewed', () => {
  beforeEach(() => {
    recentMock.latestParams = undefined
    recentMock.queryState.data.value = null
    recentMock.queryState.isLoading.value = false
    recentMock.queryState.isError.value = false
    recentMock.queryState.error.value = null
    recentMock.queryState.refetch.mockReset()
  })

  it('renders recently viewed posts in the shared paginated card', () => {
    recentMock.queryState.data.value = {
      content: [
        { postId: 1, title: 'First post' },
        { postId: 2, title: 'Second post' },
      ],
      totalPages: 4,
    }

    const wrapper = mountView()

    expect(wrapper.text()).toContain('user.tabs.recent')
    expect(wrapper.text()).toContain('First post')
    expect(wrapper.text()).toContain('Second post')
    expect(wrapper.get('[data-test="pagination"]').text()).toContain('0/4')
    expect(wrapper.get('[data-test="board-name"]').text()).toBe('true')
    expect(wrapper.get('[data-test="hide-no-column"]').text()).toBe('true')
  })

  it('renders empty state when there are no recent posts', () => {
    recentMock.queryState.data.value = {
      content: [],
      totalPages: 0,
    }

    const wrapper = mountView()

    expect(wrapper.get('[data-test="empty-state"]').text()).toBe('user.recentViewed.empty')
    expect(wrapper.find('[data-test="post-list"]').exists()).toBe(false)
  })

  it('renders loading state before the first page is available', () => {
    recentMock.queryState.isLoading.value = true

    const wrapper = mountView()

    expect(wrapper.find('.animate-spin').exists()).toBe(true)
    expect(wrapper.find('[data-test="empty-state"]').exists()).toBe(false)
  })

  it('renders error state and retries loading recent posts', async () => {
    recentMock.queryState.isError.value = true
    recentMock.queryState.error.value = new Error('recent failed')

    const wrapper = mountView()

    expect(wrapper.get('[data-test="error-state"]').text()).toContain('recent failed')
    expect(wrapper.find('[data-test="empty-state"]').exists()).toBe(false)

    await wrapper.get('[data-test="error-state"] button').trigger('click')
    expect(recentMock.queryState.refetch).toHaveBeenCalledTimes(1)
  })

  it('updates query params when page or page size changes', async () => {
    recentMock.queryState.data.value = {
      content: [{ postId: 1, title: 'First post' }],
      totalPages: 4,
    }

    const wrapper = mountView()

    expect(recentMock.latestParams?.value).toEqual({ page: 0, size: 15 })

    await wrapper.get('[data-test="pagination"]').trigger('click')
    expect(recentMock.latestParams?.value).toEqual({ page: 2, size: 15 })

    await wrapper.get('[data-test="size-change"]').trigger('click')
    expect(recentMock.latestParams?.value).toEqual({ page: 0, size: 30 })
  })
})
