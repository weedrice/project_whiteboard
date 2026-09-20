import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { VueQueryPlugin, type QueryClient } from '@tanstack/vue-query'
import { notificationApi } from '@/api/notification'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'
import { sessionQueryKey } from '@/queryAuthScope'
import { notificationListQueryKey, notificationUnreadCountQueryKey } from '../notificationQueryKeys'
import { useNotification } from '../useNotification'

const authStore = vi.hoisted(() => ({ sessionGeneration: 3, isAuthenticated: true }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/notification', () => ({
  notificationApi: { markAsRead: vi.fn(), markAllAsRead: vi.fn() },
}))

const notificationKeys = [
  notificationListQueryKey({ page: 0, size: 20 }),
  notificationListQueryKey({ page: 1, size: 20 }),
  notificationUnreadCountQueryKey,
]
const mutationKinds = ['single', 'all'] as const
type MutationKind = typeof mutationKinds[number]

async function markRead(queryClient: QueryClient, kind: MutationKind) {
  let mutate!: () => Promise<unknown>
  const wrapper = mount(defineComponent({
    setup() {
      const notifications = useNotification()
      if (kind === 'single') {
        const mutation = notifications.useMarkAsRead()
        mutate = () => mutation.mutateAsync(10)
      } else {
        const mutation = notifications.useMarkAllAsRead()
        mutate = () => mutation.mutateAsync()
      }
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  try {
    await mutate()
  } finally {
    wrapper.unmount()
  }
}

describe('notification cache invalidation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authStore.sessionGeneration = 3
    vi.mocked(notificationApi.markAsRead).mockResolvedValue(apiSuccessResponse<typeof notificationApi.markAsRead>())
    vi.mocked(notificationApi.markAllAsRead).mockResolvedValue(apiSuccessResponse<typeof notificationApi.markAllAsRead>())
  })

  it.each(mutationKinds)('refetches lists and unread count once without aborts after marking %s read', async (kind) => {
    const affected = notificationKeys.map((key) => sessionQueryKey(3, key))
    const refreshed = await expectQueriesRefetchedOnce(
      (client) => markRead(client, kind),
      affected,
      [
        ...notificationKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, ['user', 'me']),
        ...notificationKeys,
      ],
    )
    expect(refreshed).toEqual(affected)
  })

  it.each(mutationKinds)('ignores completion from an older session when marking %s read', async (kind) => {
    if (kind === 'single') {
      vi.mocked(notificationApi.markAsRead).mockImplementationOnce(async () => {
        authStore.sessionGeneration = 4
        return apiSuccessResponse<typeof notificationApi.markAsRead>()
      })
    } else {
      vi.mocked(notificationApi.markAllAsRead).mockImplementationOnce(async () => {
        authStore.sessionGeneration = 4
        return apiSuccessResponse<typeof notificationApi.markAllAsRead>()
      })
    }
    const refreshed = await expectQueriesRefetchedOnce(
      (client) => markRead(client, kind),
      [],
      [
        ...notificationKeys.map((key) => sessionQueryKey(3, key)),
        ...notificationKeys.map((key) => sessionQueryKey(4, key)),
      ],
    )
    expect(refreshed).toEqual([])
  })
})
