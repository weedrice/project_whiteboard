import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useNotification } from '../useNotification'

const mocks = vi.hoisted(() => {
    const notificationApi = {
        getNotifications: vi.fn(),
        getUnreadCount: vi.fn(),
        markAsRead: vi.fn(),
        markAllAsRead: vi.fn(),
    }
    const authApi = {
        refreshToken: vi.fn(),
    }
    const authStore = {
        isAuthenticated: true,
        accessToken: 'test-token',
        setTokens: vi.fn(),
    }
    const logger = {
        error: vi.fn(),
        warn: vi.fn(),
    }
    const queryClient = {
        invalidateQueries: vi.fn(),
        setQueryData: vi.fn(),
        setQueriesData: vi.fn(),
    }
    const queryOptions: Array<Record<string, unknown>> = []
    const mutationOptions: Array<Record<string, unknown>> = []

    const useQuery = vi.fn((options: Record<string, unknown>) => {
        queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn(),
        }
    })

    const useMutation = vi.fn((options: Record<string, unknown>) => {
        mutationOptions.push(options)
        return {
            mutateAsync: async (variables: unknown) => {
                const result = await (options.mutationFn as (v: unknown) => Promise<unknown>)(variables)
                if (options.onSuccess) {
                    const onSuccess = options.onSuccess as (data: unknown, vars: unknown, context: unknown) => void
                    onSuccess(result, variables, undefined)
                }
                return result
            },
            isLoading: ref(false),
            error: ref(null),
        }
    })

    return {
        notificationApi,
        authApi,
        authStore,
        logger,
        queryClient,
        queryOptions,
        mutationOptions,
        useQuery,
        useMutation,
    }
})

vi.mock('@/api/notification', () => ({
    notificationApi: mocks.notificationApi,
}))

vi.mock('@/api/auth', () => ({
    authApi: mocks.authApi,
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mocks.authStore,
}))

vi.mock('@/utils/logger', () => ({
    default: mocks.logger,
}))

vi.mock('@tanstack/vue-query', () => ({
    useQuery: mocks.useQuery,
    useMutation: mocks.useMutation,
    useQueryClient: () => mocks.queryClient,
}))

const createSseStream = (payload: string): ReadableStream<Uint8Array> => {
    const encoder = new TextEncoder()
    return new ReadableStream<Uint8Array>({
        start(controller) {
            controller.enqueue(encoder.encode(payload))
            controller.close()
        },
    })
}

const flushAsync = async (cycles = 4) => {
    for (let i = 0; i < cycles; i += 1) {
        await Promise.resolve()
    }
}

describe('useNotification', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
        mocks.mutationOptions.length = 0
        mocks.authStore.isAuthenticated = true
        mocks.authStore.accessToken = 'test-token'
        localStorage.clear()
    })

    it('fetches notification page via useNotifications queryFn', async () => {
        const pageResponse = { content: [{ notificationId: 1 }], number: 0, size: 20, totalElements: 1, empty: false }
        mocks.notificationApi.getNotifications.mockResolvedValueOnce({
            data: { data: pageResponse },
        })

        const { useNotifications } = useNotification()
        const params = ref({ page: 0, size: 20 })
        useNotifications(params as never)

        const options = mocks.queryOptions[0]
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect(mocks.notificationApi.getNotifications).toHaveBeenCalledWith(params.value)
        expect(result).toEqual(pageResponse)
        expect((options.placeholderData as (prev: unknown) => unknown)('keep-me')).toBe('keep-me')
    })

    it('fetches unread count with auth-aware enabled flag', async () => {
        mocks.notificationApi.getUnreadCount.mockResolvedValueOnce({
            data: { data: 7 },
        })
        mocks.authStore.isAuthenticated = false

        const { useUnreadCount } = useNotification()
        useUnreadCount()
        const options = mocks.queryOptions[0]

        expect((options.enabled as { value: boolean }).value).toBe(false)
        expect(options.refetchInterval).toBe(60000)

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toBe(7)
    })

    it('marks notification as read and invalidates related queries', async () => {
        mocks.notificationApi.markAsRead.mockResolvedValueOnce({
            data: { success: true },
        })
        const { useMarkAsRead } = useNotification()
        const mutation = useMarkAsRead()

        await mutation.mutateAsync(10)

        expect(mocks.notificationApi.markAsRead).toHaveBeenCalledWith(10)
        expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications', 'unread-count'] })
    })

    it('marks all as read and sets unread-count cache to zero', async () => {
        mocks.notificationApi.markAllAsRead.mockResolvedValueOnce({
            data: { success: true },
        })
        const { useMarkAllAsRead } = useNotification()
        const mutation = useMarkAllAsRead()

        await mutation.mutateAsync(undefined)

        expect(mocks.notificationApi.markAllAsRead).toHaveBeenCalled()
        expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        expect(mocks.queryClient.setQueryData).toHaveBeenCalledWith(['notifications', 'unread-count'], 0)
    })

    it('does not connect to SSE when token is missing', () => {
        mocks.authStore.accessToken = null as unknown as string
        const fetchMock = vi.fn()
        vi.stubGlobal('fetch', fetchMock)

        const { connectToSse } = useNotification()
        connectToSse()

        expect(fetchMock).not.toHaveBeenCalled()
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

        localStorage.setItem('refreshToken', 'refresh-token')
        mocks.authApi.refreshToken.mockResolvedValueOnce({
            data: {
                data: {
                    accessToken: 'new-access',
                    refreshToken: 'new-refresh',
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
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()
        closeSse()

        expect((firstPage.content as Array<{ notificationId: number; isRead: boolean }>)[0]).toEqual({
            notificationId: 1,
            isRead: false,
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
        await Promise.resolve()
        await Promise.resolve()
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
        await Promise.resolve()
        await Promise.resolve()
        closeSse()

        expect(mocks.logger.error).toHaveBeenCalledWith(
            'Failed to parse SSE notification:',
            expect.anything(),
        )
    })

    it('prevents duplicate connect attempts while already connecting', () => {
        const fetchMock = vi.fn(() => new Promise(() => undefined))
        vi.stubGlobal('fetch', fetchMock)

        const { connectToSse, closeSse } = useNotification()
        connectToSse()
        connectToSse()

        expect(fetchMock).toHaveBeenCalledTimes(1)
        closeSse()
    })

    it('schedules reconnect after refresh success and uses refresh token', async () => {
        const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
        const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
        localStorage.setItem('refreshToken', 'refresh-token')
        mocks.authApi.refreshToken.mockResolvedValueOnce({
            data: {
                data: {
                    accessToken: 'next-access',
                    refreshToken: 'next-refresh',
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
        await Promise.resolve()
        await Promise.resolve()
        closeSse()

        expect(mocks.authApi.refreshToken).toHaveBeenCalledWith('refresh-token')
        expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 1000)
        expect(clearTimeoutSpy).toHaveBeenCalled()
    })

    it('falls back to delayed reconnect when refresh fails', async () => {
        const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout')
        localStorage.setItem('refreshToken', 'refresh-token')
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
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()

        expect(mocks.authApi.refreshToken).toHaveBeenCalledWith('refresh-token')
        expect(setTimeoutSpy.mock.calls.some((call) => call[1] === 5000)).toBe(true)
        closeSse()
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

    it('does not remember IDs for existing first-page notifications when id is non-numeric', async () => {
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

    it('accepts non-numeric notification id payloads without recent-id dedupe', async () => {
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

        expect((firstPage.content as Array<{ notificationId: string }>)[0].notificationId).toBe('abc')
        expect(unreadCount).toBe(1)
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
        localStorage.setItem('refreshToken', 'refresh-token')

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
