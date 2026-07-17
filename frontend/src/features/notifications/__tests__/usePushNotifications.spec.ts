import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePushNotifications } from '../usePushNotifications'
import { userApi } from '@/api/user'

const mocks = vi.hoisted(() => ({
  queryOptions: null as null | { queryFn: (context: { signal: AbortSignal }) => Promise<unknown> },
  refetch: vi.fn(),
  queryState: {
    data: { __v_isRef: true, value: { enabled: true, publicKey: 'public-key' } },
    isLoading: { __v_isRef: true, value: false },
    isError: { __v_isRef: true, value: false },
    error: { __v_isRef: true, value: null as Error | null },
  },
}))

vi.mock('@tanstack/vue-query', () => ({
  QueryClient: class QueryClient {},
  QueryCache: class QueryCache {},
  MutationCache: class MutationCache {},
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  useQuery: (options: { queryFn: (context: { signal: AbortSignal }) => Promise<unknown> }) => {
    mocks.queryOptions = options
    return { ...mocks.queryState, refetch: mocks.refetch }
  },
  useMutation: () => ({
    isPending: { __v_isRef: true, value: false },
    mutateAsync: vi.fn(),
  }),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getPushPublicKey: vi.fn(),
  },
}))

vi.mock('@/features/notifications/pushSubscriptions', () => ({
  deleteBrowserPushSubscription: vi.fn(),
  getBrowserPushSubscription: vi.fn(),
  getNotificationPermission: () => 'default',
  isPushSupported: () => true,
  requestPushPermission: vi.fn(),
  saveBrowserPushSubscription: vi.fn(),
  subscribeBrowserPush: vi.fn(),
}))

describe('usePushNotifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queryOptions = null
    mocks.queryState.isLoading.value = false
    mocks.queryState.isError.value = false
    mocks.queryState.error.value = null
    vi.mocked(userApi.getPushPublicKey).mockResolvedValue({
      data: {
        success: true,
        data: { enabled: true, publicKey: 'public-key' },
      },
    } as Awaited<ReturnType<typeof userApi.getPushPublicKey>>)
  })

  it('exposes public-key errors and retry while forwarding the query abort signal', async () => {
    mocks.queryState.isError.value = true
    mocks.queryState.error.value = new Error('failed')
    const resource = usePushNotifications()
    const controller = new AbortController()

    await mocks.queryOptions?.queryFn({ signal: controller.signal })

    expect(userApi.getPushPublicKey).toHaveBeenCalledWith({ signal: controller.signal })
    expect(resource.isError.value).toBe(true)
    expect(resource.error.value).toEqual(new Error('failed'))
    expect(resource.refetch).toBe(mocks.refetch)
  })
})
