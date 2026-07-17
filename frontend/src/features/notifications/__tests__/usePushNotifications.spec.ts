import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePushNotifications } from '../usePushNotifications'
import { userApi } from '@/api/user'
import * as pushSubscriptions from '@/features/notifications/pushSubscriptions'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const mocks = vi.hoisted(() => ({
  queryOptions: null as null | { queryFn: (context: { signal: AbortSignal }) => Promise<unknown> },
  mutationOptions: [] as Array<Record<string, (...args: never[]) => unknown>>,
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
  useMutation: (options: Record<string, (...args: never[]) => unknown>) => {
    mocks.mutationOptions.push(options)
    return {
      isPending: { __v_isRef: true, value: false },
      mutateAsync: vi.fn(async (variables?: unknown) => {
        const context = options.onMutate?.(variables as never)
        const data = await options.mutationFn?.(variables as never)
        await options.onSuccess?.(data as never, variables as never, context as never)
        return data
      }),
    }
  },
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getPushPublicKey: vi.fn(),
    deleteAllPushSubscriptions: vi.fn(),
  },
}))

const authState = vi.hoisted(() => ({
  sessionGeneration: 0,
  accessToken: 'token-a' as string | null,
  user: { userId: 1 } as { userId: number } | null,
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

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
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockReset()
    vi.mocked(pushSubscriptions.requestPushPermission).mockReset()
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockReset()
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockReset()
    vi.mocked(userApi.deleteAllPushSubscriptions).mockReset()
    mocks.queryOptions = null
    mocks.mutationOptions.length = 0
    authState.sessionGeneration = 0
    authState.accessToken = 'token-a'
    authState.user = { userId: 1 }
    mocks.queryState.isLoading.value = false
    mocks.queryState.isError.value = false
    mocks.queryState.error.value = null
    vi.mocked(userApi.getPushPublicKey).mockResolvedValue({
      data: {
        success: true,
        data: { enabled: true, publicKey: 'public-key' },
      },
    } as Awaited<ReturnType<typeof userApi.getPushPublicKey>>)
    vi.mocked(userApi.deleteAllPushSubscriptions).mockResolvedValue({} as never)
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(null)
  })

  it('cancels enable before saving when the account changes during permission', async () => {
    let resolvePermission!: (permission: NotificationPermission) => void
    vi.mocked(pushSubscriptions.requestPushPermission).mockImplementationOnce(
      () => new Promise((resolve) => { resolvePermission = resolve }),
    )
    const resource = usePushNotifications()

    const enabling = resource.enablePush()
    authState.sessionGeneration = 1
    authState.accessToken = 'token-b'
    authState.user = { userId: 2 }
    notifyAuthSessionBoundary(1)
    resolvePermission('granted')

    await expect(enabling).rejects.toMatchObject({ name: 'AbortError' })
    expect(pushSubscriptions.subscribeBrowserPush).not.toHaveBeenCalled()
    expect(pushSubscriptions.saveBrowserPushSubscription).not.toHaveBeenCalled()
  })

  it('unsubscribes a browser endpoint when the session changes after server save starts', async () => {
    const unsubscribe = vi.fn(async () => true)
    const subscription = { unsubscribe } as unknown as PushSubscription
    let resolveSave!: () => void
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValueOnce('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValueOnce(subscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockImplementationOnce(
      () => new Promise((resolve) => { resolveSave = () => resolve({} as never) }),
    )
    const resource = usePushNotifications()
    const enabling = resource.enablePush()
    await vi.waitFor(() => expect(pushSubscriptions.saveBrowserPushSubscription).toHaveBeenCalled())

    authState.sessionGeneration = 1
    authState.accessToken = 'token-b'
    authState.user = { userId: 2 }
    notifyAuthSessionBoundary(1)
    resolveSave()

    await expect(enabling).rejects.toMatchObject({ name: 'AbortError' })
    expect(unsubscribe).toHaveBeenCalledOnce()
  })

  it('rolls back a newly created browser endpoint when server registration fails', async () => {
    const unsubscribe = vi.fn(async () => true)
    const subscription = { unsubscribe } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValueOnce('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValueOnce(subscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockRejectedValueOnce(new Error('network failed'))
    const resource = usePushNotifications()

    await expect(resource.enablePush()).rejects.toThrow('network failed')
    expect(unsubscribe).toHaveBeenCalledOnce()
  })

  it('repairs a browser-only subscription by registering the existing endpoint', async () => {
    const subscription = { unsubscribe: vi.fn() } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockResolvedValue({} as never)
    const resource = usePushNotifications(() => false)
    await Promise.resolve()

    expect(resource.syncState.value).toBe('browser-only')
    await resource.enablePush()
    expect(pushSubscriptions.subscribeBrowserPush).not.toHaveBeenCalled()
    expect(pushSubscriptions.saveBrowserPushSubscription).toHaveBeenCalledWith(
      subscription,
      { signal: expect.any(AbortSignal) },
    )
  })

  it('clears all server subscriptions even when the browser endpoint is already missing', async () => {
    const resource = usePushNotifications(() => true)
    await Promise.resolve()

    expect(resource.syncState.value).toBe('server-only')
    await resource.disablePush()
    expect(userApi.deleteAllPushSubscriptions).toHaveBeenCalledWith({
      signal: expect.any(AbortSignal),
    })
  })

  it('ignores a late initial subscription read after enable completes', async () => {
    let resolveInitial!: (subscription: PushSubscription | null) => void
    const subscription = { unsubscribe: vi.fn() } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveInitial = resolve }))
      .mockResolvedValueOnce(null)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValueOnce('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValueOnce(subscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockResolvedValueOnce({} as never)
    const resource = usePushNotifications(() => true)

    await resource.enablePush()
    expect(resource.syncState.value).toBe('enabled')
    resolveInitial(null)
    await Promise.resolve()

    expect(resource.syncState.value).toBe('enabled')
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
    await resource.refetch()
    expect(mocks.refetch).toHaveBeenCalled()
  })
})
