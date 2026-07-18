import { defineComponent, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { postApi } from '@/api/post'
import { usePostViewHistory } from '@/features/board/posts/detail/usePostViewHistory'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'

const sessionBoundary = vi.hoisted(() => ({ listener: null as (() => void) | null }))

vi.mock('@/queryAuthScope', () => ({
  subscribeAuthSessionBoundary: (listener: () => void) => {
    sessionBoundary.listener = listener
    return () => {
      if (sessionBoundary.listener === listener) sessionBoundary.listener = null
    }
  },
}))

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
    const enabledRef = ref(props.enabled)
    const initialLastReadCommentId = ref<number | null>(props.initialCommentId || null)
    const sessionGeneration = ref(1)
    const userId = ref<number | null>(7)
    const history = usePostViewHistory({
      postId,
      enabled: enabledRef,
      initialLastReadCommentId,
      sessionGeneration,
      userId,
    })
    return { ...history, enabledRef, postId, sessionGeneration, userId }
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
      expect.objectContaining({
        signal: expect.any(AbortSignal),
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      }),
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
      expect.objectContaining({
        signal: expect.any(AbortSignal),
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      }),
    )
  })

  it('does not move the last-read comment cursor backwards', async () => {
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    wrapper.vm.setLastReadCommentId(3)
    now = 1_000

    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledWith(
      15,
      { durationMs: 1_000, lastReadCommentId: 9 },
      expect.any(Object),
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
      expect.objectContaining({
        signal: expect.any(AbortSignal),
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      }),
    )
  })

  it('aborts and discards pending history at an authentication boundary', async () => {
    let rejectRequest!: (error: unknown) => void
    vi.mocked(postApi.updateViewHistory).mockImplementationOnce((_postId, _payload, config) => (
      new Promise((_, reject) => {
        rejectRequest = reject
        config?.signal?.addEventListener?.('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    ))
    const wrapper = mount(TestHost)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    await nextTick()

    sessionBoundary.listener?.()
    wrapper.vm.sessionGeneration = 2
    wrapper.vm.userId = 8
    rejectRequest(new Error('late failure'))
    await flushPromises()

    window.dispatchEvent(new Event('focus'))
    now = 31_000
    vi.advanceTimersByTime(30_000)
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(postApi.updateViewHistory).toHaveBeenLastCalledWith(
      15,
      { durationMs: 30_000, lastReadCommentId: 4 },
      expect.objectContaining({ skipAuthRefresh: true }),
    )
  })
})
