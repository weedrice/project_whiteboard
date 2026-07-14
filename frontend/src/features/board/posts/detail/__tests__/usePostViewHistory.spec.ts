import { defineComponent, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { postApi } from '@/api/post'
import { usePostViewHistory } from '@/features/board/posts/detail/usePostViewHistory'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'

vi.mock('@/api/post', () => ({
  postApi: {
    updateViewHistory: vi.fn(),
  },
}))

const TestHost = defineComponent({
  props: {
    enabled: { type: Boolean, default: true },
    initialCommentId: { type: Number, default: 4 },
  },
  setup(props) {
    const postId = ref(15)
    const enabled = ref(props.enabled)
    const initialLastReadCommentId = ref<number | null>(props.initialCommentId || null)
    const history = usePostViewHistory({ postId, enabled, initialLastReadCommentId })
    return { ...history, enabled, postId }
  },
  template: '<div />',
})

describe('usePostViewHistory', () => {
  let now = 0

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    now = 0
    vi.spyOn(performance, 'now').mockImplementation(() => now)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    Object.defineProperty(document, 'hasFocus', { configurable: true, value: vi.fn(() => true) })
    vi.mocked(postApi.updateViewHistory).mockResolvedValue(apiSuccessResponse<typeof postApi.updateViewHistory>())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('sends only active focused duration every thirty seconds', async () => {
    mount(TestHost)

    now = 30_000
    vi.advanceTimersByTime(30_000)
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledWith(
      15,
      { durationMs: 30_000, lastReadCommentId: 4 },
      { skipGlobalErrorHandler: true },
    )
  })

  it('flushes the latest visible comment and partial duration when focus is lost', async () => {
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    now = 5_250

    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledWith(
      15,
      { durationMs: 5_250, lastReadCommentId: 9 },
      { skipGlobalErrorHandler: true },
    )
  })

  it('does not track anonymous or unavailable post views', async () => {
    mount(TestHost, { props: { enabled: false } })
    now = 30_000

    vi.advanceTimersByTime(30_000)
    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    expect(postApi.updateViewHistory).not.toHaveBeenCalled()
  })

  it('retains failed duration and retries it with the next active interval', async () => {
    vi.mocked(postApi.updateViewHistory).mockRejectedValueOnce(new Error('offline'))
    mount(TestHost)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(1)

    window.dispatchEvent(new Event('focus'))
    await nextTick()
    now = 31_000
    vi.advanceTimersByTime(30_000)
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenLastCalledWith(
      15,
      { durationMs: 31_000, lastReadCommentId: 4 },
      { skipGlobalErrorHandler: true },
    )
  })
})
