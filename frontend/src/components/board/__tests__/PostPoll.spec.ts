import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest'
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

  afterEach(() => vi.useRealTimers())

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

  it('reactively closes the poll when its deadline passes while the view stays open', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    const wrapper = mountPoll({
      poll: poll({ closesAt: '2026-01-01T00:00:01Z' }),
    })
    await wrapper.findAll('input[type="radio"]')[0].setValue(true)
    expect(wrapper.findAll('button').at(-1)?.attributes('disabled')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(1000)

    expect(wrapper.text()).toContain('board.postDetail.poll.closed')
    expect(wrapper.findAll('input[type="radio"]')[0].attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('rechecks a distant deadline at least once per minute', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    const wrapper = mountPoll({
      poll: poll({ closesAt: '2026-01-02T00:00:00Z' }),
    })

    vi.setSystemTime(new Date('2026-01-03T00:00:00Z'))
    await vi.advanceTimersByTimeAsync(60_000)

    expect(wrapper.text()).toContain('board.postDetail.poll.closed')
  })

  it('rechecks the deadline when tab visibility changes', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    const wrapper = mountPoll({
      poll: poll({ closesAt: '2026-01-02T00:00:00Z' }),
    })

    vi.setSystemTime(new Date('2026-01-03T00:00:00Z'))
    document.dispatchEvent(new Event('visibilitychange'))
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('board.postDetail.poll.closed')
  })

  it('treats an invalid deadline as closed', () => {
    const wrapper = mountPoll({
      poll: poll({ closesAt: 'not-a-date' }),
    })

    expect(wrapper.text()).toContain('board.postDetail.poll.closed')
    expect(wrapper.findAll('input')[0].attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button')).toHaveLength(0)
  })
})
