import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useNotificationListState } from '../useNotificationListState'

const mocks = vi.hoisted(() => ({
  useNotifications: vi.fn(),
}))

vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({
    useNotifications: mocks.useNotifications,
  }),
}))

describe('useNotificationListState', () => {
  it('derives list state from notification page data without changing query params ref', () => {
    const data = ref({
      content: [{ notificationId: 1, message: 'hello' }],
      totalPages: 3,
    })
    const refetch = vi.fn()
    const query = {
      data,
      isLoading: ref(false),
      isError: ref(false),
      error: ref(null),
      refetch,
    }
    mocks.useNotifications.mockReturnValue(query)
    const params = ref({ page: 0, size: 20 })

    const state = useNotificationListState(params)

    expect(mocks.useNotifications).toHaveBeenCalledWith(params)
    expect(state.notifications.value).toEqual(data.value.content)
    expect(state.totalPages.value).toBe(3)
    expect(state.refetch).toBe(refetch)
  })

  it('keeps failure state visible while deriving empty fallback data', () => {
    const query = {
      data: ref(undefined),
      isLoading: ref(false),
      isError: ref(true),
      error: ref(new Error('failed')),
      refetch: vi.fn(),
    }
    mocks.useNotifications.mockReturnValue(query)

    const state = useNotificationListState(ref({ page: 0, size: 20 }))

    expect(state.notifications.value).toEqual([])
    expect(state.totalPages.value).toBe(0)
    expect(state.isError.value).toBe(true)
    expect(state.error.value).toBeInstanceOf(Error)
    expect(state.errorMessage.value).toBe('common.messages.loadFailed')
  })

  it('translates the shared load failure message when a translator is provided', () => {
    const query = {
      data: ref(undefined),
      isLoading: ref(false),
      isError: ref(true),
      error: ref(new Error('failed')),
      refetch: vi.fn(),
    }
    mocks.useNotifications.mockReturnValue(query)

    const state = useNotificationListState(ref({ page: 0, size: 20 }), (key) => `translated:${key}`)

    expect(state.errorMessage.value).toBe('translated:common.messages.loadFailed')
  })
})
