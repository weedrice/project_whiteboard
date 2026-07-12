import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import AllBoardsPage from '../AllBoardsPage.vue'

const refetch = vi.fn()

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAuthenticated: false }),
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoards: () => ({
      data: ref([]),
      isLoading: ref(false),
      error: ref(new Error('raw axios error')),
      refetch,
    }),
  }),
}))

vi.mock('@unhead/vue', () => ({ useHead: vi.fn() }))

const ErrorStateStub = defineComponent({
  props: { title: String, message: String, showRetry: Boolean },
  emits: ['retry'],
  setup(props, { emit }) {
    return () => h('section', { role: 'alert' }, [
      h('h3', props.title),
      h('p', props.message),
      props.showRetry ? h('button', { onClick: () => emit('retry') }, 'retry') : null,
    ])
  },
})

describe('AllBoardsPage', () => {
  it('hides raw query errors and offers a retry action', async () => {
    refetch.mockClear()
    const wrapper = mount(AllBoardsPage, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BoardGrid: true,
          BoardListSkeleton: true,
          ErrorState: ErrorStateStub,
          SubscribedBoardList: true,
        },
      },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('common.messages.loadFailed')
    expect(wrapper.text()).not.toContain('raw axios error')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(refetch).toHaveBeenCalledOnce()
  })
})
