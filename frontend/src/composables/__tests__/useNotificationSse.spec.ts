import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createSseStream,
  flushAsync,
  getNotificationMocks,
  setupNotificationTest,
} from './notificationTestHarness'
import { useNotification } from '../useNotification'

const mocks = getNotificationMocks()

describe('useNotification SSE payload handling', () => {
  beforeEach(() => {
    setupNotificationTest()
  })

  it('does not connect to SSE when token is missing', () => {
    mocks.authStore.accessToken = null as unknown as string
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse } = useNotification()
    connectToSse()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(mocks.notificationApi.openStream).not.toHaveBeenCalled()
  })

  it('applies incoming notification to first page and increments unread count', async () => {
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

    mocks.authApi.refreshToken.mockResolvedValueOnce({
      data: {
        data: {
          accessToken: 'new-access',
        },
      },
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('event: notification\ndata: {"notificationId":1,"isRead":true}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(3)
    closeSse()

    expect(mocks.notificationApi.openStream).toHaveBeenCalledWith('test-token', expect.any(AbortSignal))
    expect((firstPage.content as Array<{ notificationId: number; isRead: boolean }>)[0]).toMatchObject({
      notificationId: 1,
      isRead: false,
    })
    expect(unreadCount).toBe(1)
  })

  it('normalizes snake_case SSE notifications before caching', async () => {
    let firstPage: Record<string, unknown> = {
      content: [],
      number: 0,
      size: 20,
      totalElements: 0,
      empty: true,
    }
    mocks.queryClient.setQueriesData.mockImplementation((_filters, updater: (oldData: unknown) => unknown) => {
      firstPage = updater(firstPage) as Record<string, unknown>
      return firstPage
    })
    let unreadCount = 0
    mocks.queryClient.setQueryData.mockImplementation((_key, updater: (old: number) => number) => {
      unreadCount = updater(unreadCount)
      return unreadCount
    })
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream(
        'event: notification\n'
        + 'data: {"notification_id":7,"source_type":"COMMENT","source_id":3,"is_read":"Y","created_at":"2026-05-19T01:00:00Z","actor":{"display_name":"Alice"}}\n\n',
      ),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(mocks.normalizeNotification).toHaveBeenCalledWith(expect.objectContaining({ notification_id: 7 }))
    expect((firstPage.content as Array<{ notificationId: number; actor: { displayName: string } }>)[0]).toMatchObject({
      notificationId: 7,
      sourceType: 'COMMENT',
      sourceId: 3,
      isRead: false,
      actor: { displayName: 'Alice' },
    })
    expect(unreadCount).toBe(1)
  })

  it('skips duplicate notification IDs and does not increase unread count twice', async () => {
    let firstPage: Record<string, unknown> = {
      content: [{ notificationId: 2, isRead: false }],
      number: 0,
      size: 20,
      totalElements: 1,
      empty: false,
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
      body: createSseStream(
        'event: notification\ndata: {"notificationId":2,"isRead":true}\n\n'
        + 'event: notification\ndata: {"notificationId":2,"isRead":true}\n\n',
      ),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(2)
    closeSse()

    expect((firstPage.content as Array<{ notificationId: number }>)).toHaveLength(1)
    expect(unreadCount).toBe(0)
  })

  it('logs parse failures for malformed SSE payloads', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('event: notification\ndata: not-json\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(2)
    closeSse()

    expect(mocks.logger.error).toHaveBeenCalledWith(
      'Failed to parse SSE notification:',
      expect.anything(),
    )
  })

  it('ignores unsupported events and empty payloads', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream(
        'event: ping\ndata: {"notificationId":99,"isRead":true}\n\n'
        + 'event: notification\ndata:\n\n',
      ),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(mocks.queryClient.setQueriesData).not.toHaveBeenCalled()
    expect(mocks.queryClient.setQueryData).not.toHaveBeenCalled()
  })

  it('handles null and non-first-page caches while still processing default page payload', async () => {
    let lastResult: unknown
    let unreadCount = 0

    mocks.queryClient.setQueriesData.mockImplementation((_filter, updater: (oldData: unknown) => unknown) => {
      updater(null)
      updater({
        content: [],
        page: 1,
        size: 20,
        totalElements: 0,
        empty: true,
      })
      lastResult = updater({
        content: [],
        size: 20,
        totalElements: 0,
        empty: true,
      })
      return lastResult
    })
    mocks.queryClient.setQueryData.mockImplementation((_key, updater: ((old: number | undefined) => number) | number) => {
      unreadCount = typeof updater === 'function' ? updater(unreadCount) : updater
      return unreadCount
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream('event: notification\ndata: {"notificationId":42,"isRead":true}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect((lastResult as { content: Array<{ notificationId: number }> }).content[0].notificationId).toBe(42)
    expect(unreadCount).toBe(1)
  })

  it('uses dynamic size limit when first page size is zero', async () => {
    let firstPage: Record<string, unknown> = {
      content: [],
      number: 0,
      size: 0,
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
      body: createSseStream('event: notification\ndata: {"notificationId":66,"isRead":true}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect((firstPage.content as Array<{ notificationId: number }>)).toHaveLength(1)
    expect((firstPage.content as Array<{ notificationId: number }>)[0].notificationId).toBe(66)
    expect(unreadCount).toBe(1)
  })

  it('ignores SSE payloads without a numeric notification id', async () => {
    let firstPage: Record<string, unknown> = {
      content: [{ message: 'legacy-entry' }],
      number: 0,
      size: 20,
      totalElements: 1,
      empty: false,
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
      body: createSseStream('event: notification\ndata: {"message":"legacy-entry"}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect((firstPage.content as Array<Record<string, string>>)).toHaveLength(1)
    expect(unreadCount).toBe(0)
  })

  it('ignores non-numeric notification id payloads', async () => {
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
      body: createSseStream('event: notification\ndata: {"notificationId":"abc","isRead":true}\n\n'),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync()
    closeSse()

    expect(firstPage.content).toEqual([])
    expect(unreadCount).toBe(0)
  })

  it('evicts old notification IDs after the recent-id limit is exceeded', async () => {
    let firstPage: Record<string, unknown> = {
      content: [],
      number: 0,
      size: 1,
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

    const payloadParts: string[] = []
    for (let id = 1; id <= 201; id += 1) {
      payloadParts.push(`event: notification\ndata: {"notificationId":${id},"isRead":true}\n\n`)
    }
    payloadParts.push('event: notification\ndata: {"notificationId":1,"isRead":true}\n\n')

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: createSseStream(payloadParts.join('')),
    })
    vi.stubGlobal('fetch', fetchMock)

    const { connectToSse, closeSse } = useNotification()
    connectToSse()
    await flushAsync(6)
    closeSse()

    expect(unreadCount).toBe(202)
    expect((firstPage.content as Array<{ notificationId: number }>)[0].notificationId).toBe(1)
  })
})
