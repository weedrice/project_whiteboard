import { useAuthStore as getMutationTestAuthStore } from '@/stores/auth'
import { authMutationConfig } from '@/test/authScopedMutationOptions'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { usePushNotifications } from '../usePushNotifications'
import { userApi } from '@/api/user'
import * as pushSubscriptions from '@/features/notifications/pushSubscriptions'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const mocks = vi.hoisted(() => ({
  queryOptions: null as null | { queryFn: (context: { signal: AbortSignal }) => Promise<unknown> },
  mutationOptions: [] as Array<Record<string, (...args: never[]) => unknown>>,
  refetch: vi.fn(),
  invalidateQueries: vi.fn(),
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
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
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
    getPushSubscriptionStatus: vi.fn(),
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
  isPushSubscriptionForApplicationServerKey: vi.fn(() => true),
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
    vi.mocked(pushSubscriptions.deleteBrowserPushSubscription).mockReset()
    vi.mocked(pushSubscriptions.isPushSubscriptionForApplicationServerKey).mockReset()
    vi.mocked(pushSubscriptions.isPushSubscriptionForApplicationServerKey).mockReturnValue(true)
    vi.mocked(userApi.deleteAllPushSubscriptions).mockReset()
    vi.mocked(userApi.getPushSubscriptionStatus).mockReset().mockResolvedValue({ data: { success: true, data: false } } as never)
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
    vi.mocked(pushSubscriptions.deleteBrowserPushSubscription).mockResolvedValue({} as never)
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(null)
  })

  it('repairs this device after another device disables all subscriptions and re-enables only itself', async () => {
    const registeredEndpoints = new Set(['device-a', 'device-b'])
    const subscription = { endpoint: 'device-b', unsubscribe: vi.fn() } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockImplementation(async (endpoint) => ({
      data: { success: true, data: registeredEndpoints.has(endpoint) },
    }) as Awaited<ReturnType<typeof userApi.getPushSubscriptionStatus>>)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockImplementation(async (saved) => {
      registeredEndpoints.add(saved.endpoint)
      return {} as never
    })
    const resource = usePushNotifications(() => registeredEndpoints.size > 0)
    await flushPromises()
    expect(resource.syncState.value).toBe('enabled')

    registeredEndpoints.clear()
    registeredEndpoints.add('device-a')
    await resource.refreshBrowserSubscription()

    expect(resource.syncState.value).toBe('browser-only')
    expect(userApi.getPushSubscriptionStatus).toHaveBeenLastCalledWith('device-b', expect.objectContaining({
      expectedSession: { sessionGeneration: 0, userId: 1 },
      signal: expect.any(AbortSignal),
    }))
    await resource.enablePush()
    expect(registeredEndpoints.has('device-b')).toBe(true)
    expect(resource.syncState.value).toBe('enabled')
  })

  it('allows repair when refreshed account settings report all subscriptions removed', async () => {
    const accountEnabled = ref(true)
    const subscription = { endpoint: 'device-b' } as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockResolvedValue({ data: { success: true, data: true } } as never)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockResolvedValue({} as never)
    const resource = usePushNotifications(() => accountEnabled.value)
    await flushPromises()
    expect(resource.syncState.value).toBe('enabled')

    // Another device removes all registrations; only the settings query refetches.
    accountEnabled.value = false
    expect(resource.syncState.value).toBe('browser-only')
    expect(userApi.getPushSubscriptionStatus).toHaveBeenCalledOnce()

    // Another device re-registers itself; that does not restore this endpoint.
    accountEnabled.value = true
    expect(resource.syncState.value).toBe('browser-only')
    await resource.enablePush()
    expect(pushSubscriptions.saveBrowserPushSubscription).toHaveBeenCalledWith(subscription, authMutationConfig())
    accountEnabled.value = true
    expect(resource.syncState.value).toBe('enabled')
  })

  it('does not let a delayed membership result override refreshed disabled account settings', async () => {
    const accountEnabled = ref(true)
    let resolveStatus!: (value: Awaited<ReturnType<typeof userApi.getPushSubscriptionStatus>>) => void
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue({ endpoint: 'device-b' } as PushSubscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockImplementationOnce(() => new Promise((resolve) => { resolveStatus = resolve }))
    const resource = usePushNotifications(() => accountEnabled.value)
    await flushPromises()

    accountEnabled.value = false
    resolveStatus({ data: { success: true, data: true } } as Awaited<ReturnType<typeof userApi.getPushSubscriptionStatus>>)
    await flushPromises()

    expect(resource.syncState.value).toBe('browser-only')
    accountEnabled.value = true
    expect(resource.syncState.value).toBe('browser-only')
  })

  it('keeps a successful repair when disabled account settings arrive during registration', async () => {
    const accountEnabled = ref(true)
    const subscription = { endpoint: 'device-b' } as PushSubscription
    let completeSave!: () => void
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockImplementationOnce(() => new Promise((resolve) => {
      completeSave = () => resolve({} as never)
    }))
    const resource = usePushNotifications(() => accountEnabled.value)
    await flushPromises()
    const enabling = resource.enablePush()
    await vi.waitFor(() => expect(pushSubscriptions.saveBrowserPushSubscription).toHaveBeenCalledOnce())

    accountEnabled.value = false
    completeSave()
    await enabling
    accountEnabled.value = true

    expect(resource.syncState.value).toBe('enabled')
  })

  it('does not claim a browser endpoint registered to another account is enabled', async () => {
    const subscription = { endpoint: 'another-account-endpoint' } as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    const resource = usePushNotifications(() => true)
    await flushPromises()

    expect(resource.syncState.value).toBe('browser-only')
  })

  it('discards a late membership lookup after account transfer', async () => {
    const subscription = { endpoint: 'shared-device' } as PushSubscription
    let resolveOldStatus!: (value: Awaited<ReturnType<typeof userApi.getPushSubscriptionStatus>>) => void
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockImplementationOnce(() => new Promise((resolve) => { resolveOldStatus = resolve }))
    const resource = usePushNotifications(() => true)
    await flushPromises()
    expect(userApi.getPushSubscriptionStatus).toHaveBeenCalledOnce()
    const oldSignal = vi.mocked(userApi.getPushSubscriptionStatus).mock.calls[0][1]?.signal

    authState.sessionGeneration = 1
    authState.user = { userId: 2 }
    notifyAuthSessionBoundary(1)
    await flushPromises()
    expect(oldSignal?.aborted).toBe(true)
    expect(resource.syncState.value).toBe('browser-only')
    resolveOldStatus({ data: { success: true, data: true } } as Awaited<ReturnType<typeof userApi.getPushSubscriptionStatus>>)
    await flushPromises()

    expect(resource.syncState.value).toBe('browser-only')
  })

  it('shows a retryable error when this endpoint membership cannot be checked', async () => {
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue({ endpoint: 'device-b' } as PushSubscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockRejectedValueOnce(new Error('offline'))
    const resource = usePushNotifications(() => true)
    await flushPromises()

    expect(resource.isError.value).toBe(true)
    expect(resource.syncState.value).not.toBe('enabled')
    await resource.refreshBrowserSubscription()
    expect(resource.isError.value).toBe(false)
    expect(resource.syncState.value).toBe('browser-only')
  })

  it.each(['unsubscribe', 'refresh'] as const)('keeps repair available after server deletion succeeds but browser %s fails', async (failure) => {
    const subscription = { endpoint: 'device-b', unsubscribe: vi.fn(async () => true) } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(subscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockResolvedValue({ data: { success: true, data: true } } as never)
    const resource = usePushNotifications(() => true)
    await flushPromises()
    expect(resource.syncState.value).toBe('enabled')
    if (failure === 'unsubscribe') {
      vi.mocked(subscription.unsubscribe).mockRejectedValueOnce(new Error('browser cleanup failed'))
    } else {
      vi.mocked(pushSubscriptions.getBrowserPushSubscription)
        .mockResolvedValueOnce(subscription)
        .mockRejectedValueOnce(new Error('browser cleanup failed'))
    }

    await expect(resource.disablePush()).rejects.toThrow('browser cleanup failed')

    expect(resource.syncState.value).toBe('browser-only')
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({
      queryKey: expect.arrayContaining(['session', 0]),
    })
  })

  it('does not reuse old endpoint membership after a stale-key replacement fails to save or roll back', async () => {
    const oldSubscription = { endpoint: 'old-device', unsubscribe: vi.fn(async () => true) } as unknown as PushSubscription
    const newSubscription = { endpoint: 'new-device', unsubscribe: vi.fn(async () => false) } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription)
      .mockResolvedValueOnce(oldSubscription)
      .mockResolvedValueOnce(oldSubscription)
      .mockResolvedValueOnce(newSubscription)
    vi.mocked(userApi.getPushSubscriptionStatus).mockResolvedValue({ data: { success: true, data: true } } as never)
    vi.mocked(pushSubscriptions.isPushSubscriptionForApplicationServerKey).mockImplementation((subscription) => subscription.endpoint === newSubscription.endpoint)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValue(newSubscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockRejectedValueOnce(new Error('registration failed'))
    const resource = usePushNotifications(() => true)
    await flushPromises()
    expect(resource.syncState.value).toBe('server-only')

    await expect(resource.enablePush()).rejects.toThrow('registration failed')

    expect(resource.syncState.value).toBe('browser-only')
    expect(mocks.invalidateQueries).toHaveBeenCalled()
  })

  it('discards a delayed rollback browser lookup after the account changes', async () => {
    const subscription = { endpoint: 'old-account-device', unsubscribe: vi.fn(async () => false) } as unknown as PushSubscription
    let resolveRollback!: (value: PushSubscription | null) => void
    vi.mocked(pushSubscriptions.getBrowserPushSubscription)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(null)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveRollback = resolve }))
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValue(subscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockRejectedValueOnce(new Error('registration failed'))
    const resource = usePushNotifications(() => false)
    await flushPromises()
    const enabling = resource.enablePush().catch((error: unknown) => error)
    await vi.waitFor(() => expect(pushSubscriptions.getBrowserPushSubscription).toHaveBeenCalledTimes(3))

    authState.sessionGeneration = 1
    authState.user = { userId: 2 }
    notifyAuthSessionBoundary(1)
    await flushPromises()
    resolveRollback(subscription)
    await enabling

    expect(resource.hasBrowserSubscription.value).toBe(false)
    expect(resource.syncState.value).toBe('disabled')
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
      authMutationConfig(),
    )
  })

  it('replaces an existing browser subscription when its application server key is stale', async () => {
    const oldUnsubscribe = vi.fn(async () => true)
    const oldSubscription = { unsubscribe: oldUnsubscribe } as unknown as PushSubscription
    const nextSubscription = { unsubscribe: vi.fn(async () => true) } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(oldSubscription)
    vi.mocked(pushSubscriptions.isPushSubscriptionForApplicationServerKey).mockReturnValue(false)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.subscribeBrowserPush).mockResolvedValue(nextSubscription)
    vi.mocked(pushSubscriptions.saveBrowserPushSubscription).mockResolvedValue({} as never)
    const resource = usePushNotifications(() => true)

    await resource.enablePush()

    expect(pushSubscriptions.deleteBrowserPushSubscription).toHaveBeenCalledWith(
      oldSubscription,
      authMutationConfig(),
    )
    expect(oldUnsubscribe).toHaveBeenCalledOnce()
    expect(pushSubscriptions.subscribeBrowserPush).toHaveBeenCalledWith('public-key')
    expect(pushSubscriptions.saveBrowserPushSubscription).toHaveBeenCalledWith(
      nextSubscription,
      authMutationConfig(),
    )
  })

  it('keeps a stale-key browser subscription when server cleanup fails', async () => {
    const oldUnsubscribe = vi.fn(async () => true)
    const oldSubscription = { unsubscribe: oldUnsubscribe } as unknown as PushSubscription
    vi.mocked(pushSubscriptions.getBrowserPushSubscription).mockResolvedValue(oldSubscription)
    vi.mocked(pushSubscriptions.isPushSubscriptionForApplicationServerKey).mockReturnValue(false)
    vi.mocked(pushSubscriptions.requestPushPermission).mockResolvedValue('granted')
    vi.mocked(pushSubscriptions.deleteBrowserPushSubscription).mockRejectedValueOnce(new Error('offline'))
    const resource = usePushNotifications(() => true)

    await expect(resource.enablePush()).rejects.toThrow('offline')

    expect(oldUnsubscribe).not.toHaveBeenCalled()
    expect(pushSubscriptions.subscribeBrowserPush).not.toHaveBeenCalled()
  })

  it('clears all server subscriptions even when the browser endpoint is already missing', async () => {
    const resource = usePushNotifications(() => true)
    await Promise.resolve()

    expect(resource.syncState.value).toBe('server-only')
    await resource.disablePush()
    expect(userApi.deleteAllPushSubscriptions).toHaveBeenCalledWith(authMutationConfig({
      signal: expect.any(AbortSignal),
    }))
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

vi.mock('@/composables/useAuthScopedMutation', async () => {
    const { useMutation } = await import('@tanstack/vue-query')
    const { authScopedMutationOptions } = await import('@/test/authScopedMutationOptions')
    return { useAuthScopedMutation: (options: Record<string, any>) => useMutation(authScopedMutationOptions(options, getMutationTestAuthStore)) }
})
