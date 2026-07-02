import { vi } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { resetNotificationStreamStateForTest } from '../useNotification'

const notificationMocks = vi.hoisted(() => {
  const notificationApi = {
    getNotifications: vi.fn(),
    getUnreadCount: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    openStream: vi.fn(),
  }
  const getNotificationStreamUrl = vi.fn(() => 'https://api.example.com/api/v1/notifications/stream')
  const normalizeNotification = vi.fn((raw: Record<string, unknown>) => {
    const actor = raw.actor as Record<string, unknown> | undefined
    const displayName = String(actor?.displayName || actor?.display_name || '')
    const actorDisplayName = displayName.trim() || 'Unknown'
    return {
      notificationId: raw.notificationId || raw.notification_id || 0,
      sourceType: raw.sourceType || raw.source_type || 'SYSTEM',
      sourceId: raw.sourceId || raw.source_id || 0,
      isRead: raw.isRead === true || raw.is_read === true || raw.is_read === 'Y',
      createdAt: raw.createdAt || raw.created_at || '',
      message: raw.message || '',
      actor: {
        userId: actor?.userId || actor?.user_id || 0,
        agentId: actor?.agentId || actor?.agent_id,
        authorType: actor?.authorType || actor?.author_type,
        displayName: actor?.displayName || actor?.display_name || '',
        profileImageUrl: actor?.profileImageUrl || actor?.profile_image_url,
      },
      actorDisplayName,
      actorInitial: actorDisplayName[0] || '?',
      targetUrl: raw.targetUrl,
    }
  })
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
    getNotificationStreamUrl,
    normalizeNotification,
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
  notificationApi: notificationMocks.notificationApi,
  getNotificationStreamUrl: notificationMocks.getNotificationStreamUrl,
  normalizeNotification: notificationMocks.normalizeNotification,
}))

vi.mock('@/api/auth', () => ({
  authApi: notificationMocks.authApi,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => notificationMocks.authStore,
}))

vi.mock('@/utils/logger', () => ({
  default: notificationMocks.logger,
}))

vi.mock('@tanstack/vue-query', () => ({
  keepPreviousData: (previousData: unknown) => previousData,
  useQuery: notificationMocks.useQuery,
  useMutation: notificationMocks.useMutation,
  useQueryClient: () => notificationMocks.queryClient,
}))

export const createSseStream = (payload: string): ReadableStream<Uint8Array> => {
  const encoder = new TextEncoder()
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(payload))
      controller.close()
    },
  })
}

export const flushAsync = async (cycles = 4) => {
  for (let i = 0; i < cycles; i += 1) {
    await Promise.resolve()
  }
}

export function setupNotificationTest() {
  resetNotificationStreamStateForTest()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  notificationMocks.queryOptions.length = 0
  notificationMocks.mutationOptions.length = 0
  notificationMocks.getNotificationStreamUrl.mockReturnValue('https://api.example.com/api/v1/notifications/stream')
  notificationMocks.normalizeNotification.mockClear()
  notificationMocks.authStore.isAuthenticated = true
  notificationMocks.authStore.accessToken = 'test-token'
  notificationMocks.notificationApi.openStream.mockImplementation((token: string, signal: AbortSignal) => {
    return fetch('/api/v1/notifications/stream', {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
      },
      cache: 'no-store',
      credentials: 'same-origin',
      signal,
    })
  })
  localStorage.clear()
}

export function getNotificationMocks() {
  return notificationMocks
}
