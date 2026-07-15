import { beforeEach, describe, expect, it } from 'vitest'
import { computed, ref, type Ref } from 'vue'
import { apiSuccessResponse, pageResponseFixture } from '@/test/apiResponseFixtures'
import { getNotificationMocks, setupNotificationTest } from './notificationTestHarness'
import { useNotification } from '../useNotification'

const mocks = getNotificationMocks()

describe('useNotification queries and mutations', () => {
  beforeEach(() => {
    setupNotificationTest()
  })

  it('fetches notification page via useNotifications queryFn', async () => {
    const pageResponse = pageResponseFixture([{ notificationId: 1 }], { size: 20 })
    mocks.notificationApi.getNotifications.mockResolvedValueOnce({
      data: { data: pageResponse },
    })

    const { useNotifications } = useNotification()
    const params: Ref<{ page: number; size: number }> = ref({ page: 0, size: 20 })
    useNotifications(params)

    const options = mocks.queryOptions[0]
    const result = await (options.queryFn as () => Promise<unknown>)()

    const queryKey = options.queryKey as ReturnType<typeof computed>
    expect(queryKey.value).toEqual(['notifications', { page: 0, size: 20 }])
    expect(mocks.notificationApi.getNotifications).toHaveBeenCalledWith(params.value)
    expect(result).toEqual(pageResponse)
    expect((options.placeholderData as (prev: unknown) => unknown)('keep-me')).toBe('keep-me')

    params.value = { page: 1, size: 10 }
    expect(queryKey.value).toEqual(['notifications', { page: 1, size: 10 }])
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
    expect(options.staleTime).toBe(0)

    const result = await (options.queryFn as () => Promise<unknown>)()
    expect(result).toBe(7)
  })

  it('marks notification as read and invalidates related queries', async () => {
    mocks.notificationApi.markAsRead.mockResolvedValueOnce(apiSuccessResponse<typeof mocks.notificationApi.markAsRead>())
    const { useMarkAsRead } = useNotification()
    const mutation = useMarkAsRead()

    await mutation.mutateAsync(10)

    expect(mocks.notificationApi.markAsRead).toHaveBeenCalledWith(10)
    expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications', 'unread-count'] })
  })

  it('marks all as read and sets unread-count cache to zero', async () => {
    mocks.notificationApi.markAllAsRead.mockResolvedValueOnce(apiSuccessResponse<typeof mocks.notificationApi.markAllAsRead>())
    const { useMarkAllAsRead } = useNotification()
    const mutation = useMarkAllAsRead()

    await mutation.mutateAsync(undefined)

    expect(mocks.notificationApi.markAllAsRead).toHaveBeenCalled()
    expect(mocks.queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(mocks.queryClient.setQueryData).toHaveBeenCalledWith(['notifications', 'unread-count'], 0)
  })
})
