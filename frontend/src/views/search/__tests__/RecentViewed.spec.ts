import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref, type Ref } from 'vue'
import { createPaginationStub } from '@/test/vue-test-helpers'

import type RecentViewedComponent from '../RecentViewed.vue'

type RecentViewed = typeof RecentViewedComponent
type RecentParams = { page: number; size: number }

let RecentViewed: RecentViewed
let latestParams: Ref<RecentParams> | undefined
let queryState: {
  data: ReturnType<typeof ref<unknown>>
  isLoading: ReturnType<typeof ref<boolean>>
  isError: ReturnType<typeof ref<boolean>>
  error: ReturnType<typeof ref<Error | null>>
  refetch: ReturnType<typeof vi.fn>
}

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
  beforeEach(async () => {
    vi.resetModules()
    latestParams = undefined
    queryState = {
      data: ref(null),
      isLoading: ref(false),
      isError: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }

    vi.doMock('@/composables/useUser', () => ({
      useUser: () => ({
        useRecentlyViewedPosts: (params: Ref<RecentParams>) => {
          latestParams = params
          return queryState
        },
      }),
    }))

    vi.doMock('@/utils/postNavigation', () => ({
      isInquiryPostItem: vi.fn(() => false),
      resolveBoardRoute: vi.fn(() => ({ name: 'board-detail' })),
      resolvePostDetailRoute: vi.fn(() => ({ name: 'post-detail' })),
    }))

    vi.doMock('vue-i18n', async (importOriginal) => {
      const actual = await importOriginal<typeof import('vue-i18n')>()

      return {
        ...actual,
        useI18n: () => ({
          t: (key: string) => key,
        }),
      }
    })

    RecentViewed = (await import('../RecentViewed.vue')).default
  })

  it('renders recently viewed posts in the shared paginated card', () => {
    queryState.data.value = {
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
    queryState.data.value = {
      content: [],
      totalPages: 0,
    }

    const wrapper = mountView()

    expect(wrapper.get('[data-test="empty-state"]').text()).toBe('user.recentViewed.empty')
    expect(wrapper.find('[data-test="post-list"]').exists()).toBe(false)
  })

  it('renders loading state before the first page is available', () => {
    queryState.isLoading.value = true

    const wrapper = mountView()

    expect(wrapper.find('.animate-spin').exists()).toBe(true)
    expect(wrapper.find('[data-test="empty-state"]').exists()).toBe(false)
  })

  it('renders error state and retries loading recent posts', async () => {
    queryState.isError.value = true
    queryState.error.value = new Error('recent failed')

    const wrapper = mountView()

    expect(wrapper.get('[data-test="error-state"]').text()).toContain('recent failed')
    expect(wrapper.find('[data-test="empty-state"]').exists()).toBe(false)

    await wrapper.get('[data-test="error-state"] button').trigger('click')
    expect(queryState.refetch).toHaveBeenCalledTimes(1)
  })

  it('updates query params when page or page size changes', async () => {
    queryState.data.value = {
      content: [{ postId: 1, title: 'First post' }],
      totalPages: 4,
    }

    const wrapper = mountView()

    expect(latestParams?.value).toEqual({ page: 0, size: 15 })

    await wrapper.get('[data-test="pagination"]').trigger('click')
    expect(latestParams?.value).toEqual({ page: 2, size: 15 })

    await wrapper.get('[data-test="size-change"]').trigger('click')
    expect(latestParams?.value).toEqual({ page: 0, size: 30 })
  })
})
