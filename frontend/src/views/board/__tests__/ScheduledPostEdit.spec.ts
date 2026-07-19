import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ScheduledPostEdit from '../ScheduledPostEdit.vue'

const state = vi.hoisted(() => ({
  scheduledPost: null as null | { scheduledPostId: number, boardUrl: string, status: string },
  isLoading: false,
  isError: false,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/features/board/posts/form/usePostFormRouteShell', async () => {
  const { ref } = await import('vue')
  return {
    usePostFormRouteShell: () => ({
      postFormRef: ref(null),
      router: { push: vi.fn() },
      route: { params: { scheduledPostId: '7' } },
      handleCancel: vi.fn(),
    }),
  }
})

vi.mock('@/features/board/posts/queries/usePost', async () => {
  const { computed } = await import('vue')
  return {
    usePost: () => ({
      useScheduledPostDetail: () => ({
        data: computed(() => state.scheduledPost),
        isLoading: computed(() => state.isLoading),
        isError: computed(() => state.isError),
        refetch: vi.fn(),
      }),
    }),
  }
})

function mountView() {
  return mount(ScheduledPostEdit, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        PostForm: { template: '<div data-testid="post-form" />' },
        BaseSpinner: { template: '<div data-testid="spinner" />' },
        ErrorState: {
          props: ['message'],
          template: '<div data-testid="error-state">{{ message }}</div>',
        },
      },
    },
  })
}

describe('ScheduledPostEdit', () => {
  it.each(['SCHEDULED', 'FAILED'])('renders the editor for %s posts', async (status) => {
    state.scheduledPost = { scheduledPostId: 7, boardUrl: 'free', status }
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="post-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(false)
  })

  it.each(['PUBLISHING', 'PUBLISHED', 'CANCELED'])('blocks the editor for %s posts', async (status) => {
    state.scheduledPost = { scheduledPostId: 7, boardUrl: 'free', status }
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="post-form"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="error-state"]').text()).toBe('board.writePost.scheduledNotEditable')
  })
})
