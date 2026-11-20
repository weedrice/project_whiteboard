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

function deferredHistoryRequest() {
  let resolve!: (value: Awaited<ReturnType<typeof postApi.updateViewHistory>>) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<Awaited<ReturnType<typeof postApi.updateViewHistory>>>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve: () => resolve(apiSuccessResponse<typeof postApi.updateViewHistory>()), reject }
}

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
      { durationMs: 30_000, viewSessionId: expect.any(String), sessionDurationMs: 30_000 },
      expect.objectContaining({
        expectedSession: { sessionGeneration: 1, userId: 7 },
        signal: expect.any(AbortSignal),
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      }),
    )
  })

  it('retries a changed cursor until confirmed, then omits it on duration-only requests without randomUUID', async () => {
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) })
    try {
      vi.mocked(postApi.updateViewHistory).mockRejectedValueOnce(new Error('response lost'))
      const wrapper = mount(TestHost)
      wrapper.vm.setLastReadCommentId(9)
      now = 1_000
      wrapper.vm.flush()
      await flushPromises()
      now = 2_000
      wrapper.vm.flush()
      await flushPromises()
      now = 3_000
      wrapper.vm.flush()
      await flushPromises()

      const payloads = vi.mocked(postApi.updateViewHistory).mock.calls.map(([, payload]) => payload)
      const id = payloads[0]!.viewSessionId
      expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
      expect(payloads).toEqual([
        { durationMs: 1_000, sessionDurationMs: 1_000, viewSessionId: id, lastReadCommentId: 9 },
        { durationMs: 2_000, sessionDurationMs: 2_000, viewSessionId: id, lastReadCommentId: 9 },
        { durationMs: 1_000, sessionDurationMs: 3_000, viewSessionId: id },
      ])
      wrapper.unmount()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('saves time without the rejected cursor and sends a newer cursor on the next flush', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    wrapper.vm.setLastReadCommentId(12)
    pending.reject({ isAxiosError: true, response: { status: 400, data: { error: { code: 'C001' } } } })
    await flushPromises()
    window.dispatchEvent(new Event('focus'))
    now = 2_000
    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    const calls = vi.mocked(postApi.updateViewHistory).mock.calls
    const id = calls[0]![1].viewSessionId
    expect(calls.map(([, payload]) => payload)).toEqual([
      { durationMs: 1_000, sessionDurationMs: 1_000, viewSessionId: id, lastReadCommentId: 9 },
      { durationMs: 1_000, sessionDurationMs: 1_000, viewSessionId: id },
      { durationMs: 1_000, sessionDurationMs: 2_000, viewSessionId: id, lastReadCommentId: 12 },
    ])
    expect(calls[1]![2]!.signal).toBe(calls[0]![2]!.signal)
    wrapper.unmount()
  })

  it('can advance from 4 to 8 after 10 is rejected and does not resubmit 10 when observed again', async () => {
    const rejected = { isAxiosError: true, response: { status: 400, data: { error: { code: 'C001' } } } }
    vi.mocked(postApi.updateViewHistory).mockRejectedValueOnce(rejected)
    const wrapper = mount(TestHost, { props: { initialCommentId: 4 } })
    wrapper.vm.setLastReadCommentId(10)
    now = 1_000
    wrapper.vm.flush()
    await flushPromises()

    wrapper.vm.setLastReadCommentId(8)
    wrapper.vm.setLastReadCommentId(10)
    now = 2_000
    wrapper.vm.flush()
    await flushPromises()

    wrapper.vm.setLastReadCommentId(10)
    now = 3_000
    wrapper.vm.flush()
    await flushPromises()
    const calls = vi.mocked(postApi.updateViewHistory).mock.calls
    const id = calls[0]![1].viewSessionId
    expect(calls.map(([, payload]) => payload)).toEqual([
      { durationMs: 1_000, sessionDurationMs: 1_000, viewSessionId: id, lastReadCommentId: 10 },
      { durationMs: 1_000, sessionDurationMs: 1_000, viewSessionId: id },
      { durationMs: 1_000, sessionDurationMs: 2_000, viewSessionId: id, lastReadCommentId: 8 },
      { durationMs: 1_000, sessionDurationMs: 3_000, viewSessionId: id },
    ])
    wrapper.unmount()
  })

  it.each(['accepted', 'rejected'] as const)(
    'keeps a lower observation made during a pending request only when needed after the cursor is %s',
    async (outcome) => {
      const pending = deferredHistoryRequest()
      vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
      const wrapper = mount(TestHost, { props: { initialCommentId: 4 } })
      wrapper.vm.setLastReadCommentId(10)
      wrapper.vm.flush()
      wrapper.vm.setLastReadCommentId(8)

      if (outcome === 'accepted') pending.resolve()
      else pending.reject({ isAxiosError: true, response: { status: 400, data: { error: { code: 'C001' } } } })
      await flushPromises()
      wrapper.vm.flush()
      await flushPromises()
      wrapper.vm.setLastReadCommentId(8)
      wrapper.vm.setLastReadCommentId(10)
      wrapper.vm.flush()
      await flushPromises()

      const cursors = vi.mocked(postApi.updateViewHistory).mock.calls.map(([, payload]) => payload.lastReadCommentId)
      expect(cursors).toEqual(outcome === 'accepted' ? [10] : [10, 8])
      wrapper.unmount()
    },
  )

  it('stops after a failed duration-only retry on disposal', async () => {
    const pending = deferredHistoryRequest()
    const rejected = { isAxiosError: true, response: { status: 400, data: { error: { code: 'C001' } } } }
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise).mockRejectedValueOnce(rejected)
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    wrapper.unmount()
    pending.reject(rejected)
    await flushPromises()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(vi.mocked(postApi.updateViewHistory).mock.calls[1]![1]).not.toHaveProperty('lastReadCommentId')
    expect(sessionBoundary.listener).toBeNull()
  })

  it('does not retry a rejected cursor after the user session changes', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    wrapper.vm.userId = 8
    wrapper.vm.sessionGeneration = 2
    pending.reject({ isAxiosError: true, response: { status: 400, data: { error: { code: 'C001' } } } })
    await flushPromises()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('drains more than a day of unsent duration in capped final requests', async () => {
    const wrapper = mount(TestHost)
    now = 86_400_005
    wrapper.unmount()
    await flushPromises()

    const calls = vi.mocked(postApi.updateViewHistory).mock.calls
    expect(calls.map((call) => [call[1].durationMs, call[1].sessionDurationMs])).toEqual([
      [86_400_000, 86_400_000], [5, 86_400_005],
    ])
    expect(calls[1]![1].viewSessionId).toBe(calls[0]![1].viewSessionId)
  })

  it('flushes the latest visible comment and partial duration when focus is lost', async () => {
    const wrapper = mount(TestHost)
    wrapper.vm.setLastReadCommentId(9)
    now = 5_250

    window.dispatchEvent(new Event('blur'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledWith(
      15,
      { durationMs: 5_250, lastReadCommentId: 9, viewSessionId: expect.any(String), sessionDurationMs: 5_250 },
      expect.objectContaining({
        expectedSession: { sessionGeneration: 1, userId: 7 },
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
      { durationMs: 1_000, lastReadCommentId: 9, viewSessionId: expect.any(String), sessionDurationMs: 1_000 },
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

    const calls = vi.mocked(postApi.updateViewHistory).mock.calls
    expect(calls[0]![1].viewSessionId).toEqual(calls[1]![1].viewSessionId)
    expect(calls[0]![1].sessionDurationMs).toBe(1_000)
    expect(postApi.updateViewHistory).toHaveBeenLastCalledWith(
      15,
      { durationMs: 31_000, viewSessionId: expect.any(String), sessionDurationMs: 31_000 },
      expect.objectContaining({
        expectedSession: { sessionGeneration: 1, userId: 7 },
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
      { durationMs: 30_000, viewSessionId: expect.any(String), sessionDurationMs: 30_000 },
      expect.objectContaining({ skipAuthRefresh: true }),
    )
  })

  it('flushes only the final delta and cursor after an in-flight request settles after unmount', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    now = 30_000
    vi.advanceTimersByTime(30_000)

    now = 35_000
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(1)
    expect(sessionBoundary.listener).not.toBeNull()

    now = 90_000
    pending.resolve()
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(vi.mocked(postApi.updateViewHistory).mock.calls.map((call) => call[1])).toEqual([
      { durationMs: 30_000, viewSessionId: expect.any(String), sessionDurationMs: 30_000 },
      { durationMs: 5_000, lastReadCommentId: 9, viewSessionId: expect.any(String), sessionDurationMs: 35_000 },
    ])
    expect(sessionBoundary.listener).toBeNull()
    vi.advanceTimersByTime(90_000)
    await flushPromises()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
  })

  it('flushes a cursor-only change queued behind a pending request', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    pending.resolve()
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(postApi.updateViewHistory).toHaveBeenLastCalledWith(
      15, { durationMs: 0, lastReadCommentId: 9, viewSessionId: expect.any(String), sessionDurationMs: 1_000 }, expect.any(Object),
    )
    expect(sessionBoundary.listener).toBeNull()
  })

  it.each(['success', 'failure'] as const)('retries unacknowledged final data once after %s', async (outcome) => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    now = 1_000
    window.dispatchEvent(new Event('blur'))
    wrapper.unmount()
    if (outcome === 'success') pending.resolve()
    else pending.reject(new Error('response lost'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(outcome === 'failure' ? 2 : 1)
    const calls = vi.mocked(postApi.updateViewHistory).mock.calls
    const retriedPayloads = calls.slice(1).map(([, payload]) => payload)
    expect(retriedPayloads).toEqual(outcome === 'failure' ? [calls[0]![1]] : [])
    expect(sessionBoundary.listener).toBeNull()
  })

  it('retries the cumulative final duration after an unknown outcome without retrying forever', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory)
      .mockReturnValueOnce(pending.promise)
      .mockRejectedValueOnce(new Error('final response lost'))
    const wrapper = mount(TestHost)
    now = 30_000
    vi.advanceTimersByTime(30_000)
    now = 35_000
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    pending.reject(new Error('first response lost'))
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(postApi.updateViewHistory).toHaveBeenLastCalledWith(
      15, { durationMs: 35_000, lastReadCommentId: 9, viewSessionId: expect.any(String), sessionDurationMs: 35_000 }, expect.any(Object),
    )
    expect(sessionBoundary.listener).toBeNull()
    vi.advanceTimersByTime(90_000)
    await flushPromises()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
  })

  it('aborts an unmounted pending request at the session boundary and discards its final follow-up', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    now = 30_000
    vi.advanceTimersByTime(30_000)
    now = 35_000
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    const signal = vi.mocked(postApi.updateViewHistory).mock.calls[0]![2]!.signal!
    expect(signal.aborted).toBe(false)
    sessionBoundary.listener?.()
    expect(signal.aborted).toBe(true)
    pending.resolve()
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(1)
    expect(sessionBoundary.listener).toBeNull()
  })

  it('keeps the boundary subscription until the final follow-up settles', async () => {
    const first = deferredHistoryRequest()
    const final = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(first.promise).mockReturnValueOnce(final.promise)
    const wrapper = mount(TestHost)
    now = 30_000
    vi.advanceTimersByTime(30_000)
    now = 35_000
    wrapper.unmount()
    first.resolve()
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(sessionBoundary.listener).not.toBeNull()
    const signal = vi.mocked(postApi.updateViewHistory).mock.calls[1]![2]!.signal!
    sessionBoundary.listener?.()
    expect(signal.aborted).toBe(true)
    final.reject(new DOMException('Aborted', 'AbortError'))
    await flushPromises()
    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(2)
    expect(sessionBoundary.listener).toBeNull()
  })

  it('does not send a final follow-up when the session identity changes without a boundary notification', async () => {
    const pending = deferredHistoryRequest()
    vi.mocked(postApi.updateViewHistory).mockReturnValueOnce(pending.promise)
    const wrapper = mount(TestHost)
    now = 30_000
    vi.advanceTimersByTime(30_000)
    now = 35_000
    wrapper.unmount()
    wrapper.vm.sessionGeneration = 2
    wrapper.vm.userId = 8
    pending.resolve()
    await flushPromises()

    expect(postApi.updateViewHistory).toHaveBeenCalledTimes(1)
    expect(sessionBoundary.listener).toBeNull()
  })
})
