import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import PostPoll from '../PostPoll.vue'
import type { PostPoll as PostPollType } from '@/types'

const voteMutateAsync = vi.hoisted(() => vi.fn())
const deleteMutateAsync = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params?.count != null ? `${key}:${params.count}` : key,
  }),
}))

vi.mock('@/features/board/posts/queries/usePost', () => ({
  usePost: () => ({
    useVotePoll: () => ({ mutateAsync: voteMutateAsync, isPending: ref(false) }),
    useDeletePollVote: () => ({ mutateAsync: deleteMutateAsync, isPending: ref(false) }),
  }),
}))

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    type: { type: String, default: 'button' },
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', {
      type: props.type,
      disabled: props.disabled || props.loading,
      onClick: () => emit('click'),
    }, slots.default?.())
  },
})

const poll = (overrides: Partial<PostPollType> = {}): PostPollType => ({
  pollId: 1,
  question: 'Choose one',
  multipleChoiceEnabled: false,
  anonymousEnabled: false,
  closesAt: null,
  options: [
    { optionId: 10, optionText: 'First', sortOrder: 0, voteCount: 2, selected: false },
    { optionId: 11, optionText: 'Second', sortOrder: 1, voteCount: 1, selected: false },
  ],
  ...overrides,
})

const mountPoll = (props: { poll?: PostPollType; isAuthenticated?: boolean } = {}) => mount(PostPoll, {
  props: {
    postId: 99,
    poll: props.poll ?? poll(),
    isAuthenticated: props.isAuthenticated ?? true,
  },
  global: {
    stubs: { BaseButton: BaseButtonStub },
  },
})

describe('PostPoll', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits a single selected option', async () => {
    const wrapper = mountPoll()

    await wrapper.findAll('input[type="radio"]')[1].setValue(true)
    await wrapper.findAll('button').at(-1)?.trigger('click')

    expect(voteMutateAsync).toHaveBeenCalledWith({
      postId: 99,
      data: { optionIds: [11] },
    })
  })

  it('deletes the current vote', async () => {
    const wrapper = mountPoll({
      poll: poll({
        options: [
          { optionId: 10, optionText: 'First', sortOrder: 0, voteCount: 2, selected: true },
          { optionId: 11, optionText: 'Second', sortOrder: 1, voteCount: 1, selected: false },
        ],
      }),
    })

    await wrapper.findAll('button')[0].trigger('click')

    expect(deleteMutateAsync).toHaveBeenCalledWith(99)
  })
})
