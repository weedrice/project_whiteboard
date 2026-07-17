import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createSseStream,
  createTestNotificationStreamController as useNotification,
  flushAsync,
  getNotificationMocks,
  setupNotificationTest,
} from './notificationTestHarness'

const mocks = getNotificationMocks()

describe('useNotification SSE connection lifecycle', () => {
  beforeEach(() => {
    setupNotificationTest()
  })

  it('prevents duplicate connect attempts while already connecting', () => {
    const fetchMock = vi.fn(() => new Promise(() => undefined))
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    connectToSse()

    expect(mocks.notificationApi.openStream).toHaveBeenCalledTimes(1)
    expect(mocks.notificationApi.openStream).toHaveBeenCalledWith('test-token', expect.any(AbortSignal))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    closeSse()
  })

  it('prevents duplicate streams across composable instances', () => {
    const fetchMock = vi.fn(() => new Promise(() => undefined))
    vi.stubGlobal('fetch', fetchMock)

    const first = useNotification()
    const second = useNotification()
    first.connectToSse()
    second.connectToSse()

    expect(mocks.notificationApi.openStream).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    first.closeSse()
  })

  it('schedules reconnect after refresh success', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    mocks.authApi.refreshToken.mockResolvedValueOnce({
      data: {
        data: {
          accessToken: 'next-access',
        },
      },
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      body: null,
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(6)

    expect(mocks.authApi.refreshToken).toHaveBeenCalledWith({
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
      signal: expect.any(AbortSignal),
    })
    expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 1000)
    closeSse()
    expect(clearTimeoutSpy).toHaveBeenCalled()
  })

  it('falls back to delayed reconnect when refresh fails', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    mocks.authApi.refreshToken.mockReset()
    mocks.authApi.refreshToken.mockImplementation(async () => {
      throw new Error('refresh failed')
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      body: null,
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(3)

    expect(mocks.authApi.refreshToken).toHaveBeenCalledWith({
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
      signal: expect.any(AbortSignal),
    })
    expect(setTimeoutSpy.mock.calls.some((call) => call[1] === 5000)).toBe(true)
    closeSse()
  })

  it('backs off without refreshing credentials after an SSE protocol limit violation', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('x'.repeat(64 * 1024 + 1)),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(4)

    expect(mocks.authApi.refreshToken).not.toHaveBeenCalled()
    expect(setTimeoutSpy.mock.calls.some((call) => call[1] === 5000)).toBe(true)
    expect(mocks.logger.warn).toHaveBeenCalledWith(
      'SSE connection dropped:',
      expect.objectContaining({ name: 'SseProtocolLimitError' }),
    )
    closeSse()
  })

  it('handles keep-alive comments and blank event names as default message events', async () => {
    let firstPage: Record<string, unknown> = {
      content: [],
      number: 0,
      size: 20,
      totalElements: 0,
      empty: true,
    }
    let unreadCount = 0

    mocks.queryClient.setQueriesData.mockImplementation((_filter, updater: (oldData: unknown) => unknown) => {
      firstPage = updater(firstPage) as Record<string, unknown>
      return firstPage
    })
    mocks.queryClient.setQueryData.mockImplementation((_key, updater: ((old: number | undefined) => number) | number) => {
      unreadCount = typeof updater === 'function' ? updater(unreadCount) : updater
      return unreadCount
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream(':keepalive\nevent:   \ndata: {"notificationId":88,"isRead":true}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect((firstPage.content as Array<{ notificationId: number }>)[0].notificationId).toBe(88)
    expect(unreadCount).toBe(1)
  })

  it('ignores trailing non-data buffer fragments when stream ends', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('event: notification'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(mocks.queryClient.setQueryData).not.toHaveBeenCalled()
  })

  it('ignores unknown SSE fields and covers non-data parsing path', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('id: 100\nretry: 1000\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(mocks.queryClient.setQueriesData).not.toHaveBeenCalled()
  })

  it('does not schedule reconnect when manually closed during refresh retry', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')

    let rejectRefresh!: (reason?: unknown) => void
    const refreshPromise = new Promise((_resolve, reject) => {
      rejectRefresh = reject
    })
    mocks.authApi.refreshToken.mockReturnValueOnce(refreshPromise)

    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      body: null,
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(2)
    closeSse()
    rejectRefresh(new Error('refresh failed after close'))
    await flushAsync()

    expect(setTimeoutSpy).not.toHaveBeenCalledWith(expect.any(Function), 1000)
    expect(setTimeoutSpy).not.toHaveBeenCalledWith(expect.any(Function), 5000)
  })

  it('flushes buffered trailing data line without terminal newline', async () => {
    let firstPage: Record<string, unknown> = {
      content: [],
      number: 0,
      size: 10,
      totalElements: 0,
      empty: true,
    }
    let unreadCount = 0

    mocks.queryClient.setQueriesData.mockImplementation((_filter, updater: (oldData: unknown) => unknown) => {
      firstPage = updater(firstPage) as Record<string, unknown>
      return firstPage
    })
    mocks.queryClient.setQueryData.mockImplementation((_key, updater: ((old: number | undefined) => number) | number) => {
      unreadCount = typeof updater === 'function' ? updater(unreadCount) : updater
      return unreadCount
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('data: {"notificationId":55,"isRead":true}'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect((firstPage.content as Array<{ notificationId: number }>)[0].notificationId).toBe(55)
    expect(unreadCount).toBe(1)
  })

  it('warns and reconnects when SSE response body is empty', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: null,
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(mocks.logger.warn).toHaveBeenCalledWith('SSE connection dropped:', expect.any(Error))
    expect(setTimeoutSpy.mock.calls.some((call) => call[1] === 5000)).toBe(true)
  })

  it('does not reconnect on AbortError', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    const abortError = new DOMException('aborted', 'AbortError')
    const fetchMock = vi.fn().mockRejectedValue(abortError)
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse } = useNotification()
    connectToSse()
    await flushAsync()

    expect(mocks.logger.warn).not.toHaveBeenCalledWith('SSE connection dropped:', expect.anything())
    expect(setTimeoutSpy).not.toHaveBeenCalledWith(expect.any(Function), 5000)
  })

  it('does not schedule reconnect when stream was manually closed before failure', async () => {
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
    const fetchMock = vi.fn().mockRejectedValue(new Error('stream failed'))
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    closeSse()
    await flushAsync()

    expect(setTimeoutSpy).not.toHaveBeenCalled()
  })

  it('swallows reader.cancel errors during stream cleanup', async () => {
    const cancel = vi.fn().mockRejectedValue(new Error('cancel failed'))
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: {
        getReader: () => ({
          read: () => Promise.resolve({ done: true, value: undefined }),
          cancel,
        }),
      },
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    closeSse()
    await flushAsync()

    expect(cancel).toHaveBeenCalled()
  })

  it('runs scheduled reconnect timer and retries stream connection', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 503, body: null })
      .mockRejectedValueOnce(new DOMException('aborted', 'AbortError'))
    vi.stubGlobal('fetch', fetchMock)

    try {
      const { connectToSse } = useNotification()
      connectToSse()
      await flushAsync()

      expect(fetchMock).toHaveBeenCalledTimes(1)

      vi.advanceTimersByTime(5000)
      await flushAsync()

      expect(fetchMock).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('clears pending reconnect timer when manually connecting again', async () => {
    vi.useFakeTimers()
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 500, body: null })
      .mockRejectedValueOnce(new DOMException('aborted', 'AbortError'))
    vi.stubGlobal('fetch', fetchMock)

    try {
      const { connectToSse } = useNotification()
      connectToSse()
      await flushAsync()

      connectToSse()
      await flushAsync()

      expect(clearTimeoutSpy).toHaveBeenCalled()
      expect(fetchMock).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('waits for browser online event before reconnecting while offline', async () => {
    vi.useFakeTimers()
    Object.defineProperty(navigator, 'onLine', {
      configurable: true,
      value: false,
    })
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: false, status: 503, body: null })
    vi.stubGlobal('fetch', fetchMock)

    try {
      const { connectToSse } = useNotification()
      connectToSse()
      await flushAsync()

      vi.advanceTimersByTime(60000)
      await flushAsync()

      expect(fetchMock).toHaveBeenCalledTimes(1)

      Object.defineProperty(navigator, 'onLine', {
        configurable: true,
        value: true,
      })
      window.dispatchEvent(new Event('online'))
      vi.advanceTimersByTime(0)
      await flushAsync()

      expect(fetchMock).toHaveBeenCalledTimes(2)
    } finally {
      Object.defineProperty(navigator, 'onLine', {
        configurable: true,
        value: true,
      })
      vi.useRealTimers()
    }
  })

  it('stops reconnect loop when refresh fails with an auth status', async () => {
    vi.useFakeTimers()
    mocks.authApi.refreshToken.mockRejectedValueOnce({
      response: { status: 401 },
    })
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: false, status: 401, body: null })
    vi.stubGlobal('fetch', fetchMock)

    try {
      const { connectToSse } = useNotification()
      connectToSse()
      await flushAsync()

      vi.advanceTimersByTime(60000)
      await flushAsync()

      expect(mocks.authApi.refreshToken).toHaveBeenCalledTimes(1)
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(mocks.handleTerminalAuthFailure).toHaveBeenCalledWith(
        401,
        mocks.authStore,
        { generation: 0, accessToken: 'test-token' },
      )
    } finally {
      vi.useRealTimers()
    }
  })
})
