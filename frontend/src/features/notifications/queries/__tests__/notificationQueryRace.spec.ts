import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { createDeferred, type Deferred } from '@/test/async'
import { pageResponse } from '@/test/factories'
import { normalizeNotification } from '@/api/notification'
import { createRecentNotificationIdCache } from '../../stream/recentNotificationIdCache'
import { applyIncomingNotificationToCache } from '../notificationCacheUpdater'
import { notificationListQueryKey, notificationUnreadCountQueryKey } from '../notificationQueryKeys'
import { useNotification } from '../useNotification'

const auth = vi.hoisted(() => ({
  sessionGeneration: 1, user: { userId: 1 }, accessToken: 'token', isAuthenticated: true,
  fetchUser: vi.fn(async () => true), setTokens: vi.fn(),
  applyTokenIfCurrent: vi.fn(() => true), clearSessionState: vi.fn(),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

const event = (id: number, groupCount = 1) => normalizeNotification({
  notificationId: id, groupCount, notificationType: 'COMMENT', sourceType: 'POST', sourceId: 3,
  isRead: false, message: `event ${id}/${groupCount}`, createdAt: '2026-10-26T00:00:00Z',
  lastEventAt: `2026-10-26T00:0${groupCount}:00Z`, actor: { userId: 2, displayName: 'Writer' },
})
const pageKey = (generation = 1) => sessionQueryKey(generation, notificationListQueryKey({ page: 0, size: 20 }))
const countKey = (generation = 1) => sessionQueryKey(generation, notificationUnreadCountQueryKey)
const emptyPage = () => pageResponse([], { size: 20, totalPages: 0 })
const originalAdapter = api.defaults.adapter
let client: QueryClient
let wrapper: VueWrapper | undefined
let recent: ReturnType<typeof createRecentNotificationIdCache>
let requests: Array<{ config: InternalAxiosRequestConfig; response: Deferred<unknown> }>
let list: ReturnType<ReturnType<typeof useNotification>['useNotifications']>
let count: ReturnType<ReturnType<typeof useNotification>['useUnreadCount']>
let markAll: ReturnType<ReturnType<typeof useNotification>['useMarkAllAsRead']>
let markOne: ReturnType<ReturnType<typeof useNotification>['useMarkAsRead']>

beforeEach(() => {
  auth.sessionGeneration = 1
  auth.user = { userId: 1 }
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user.userId)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity }, mutations: { retry: false } } })
  recent = createRecentNotificationIdCache(200)
  requests = []
  api.defaults.adapter = async config => {
    const response = createDeferred<unknown>()
    requests.push({ config, response })
    const data = await response.promise
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data } }
  }
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  client.clear()
  api.defaults.adapter = originalAdapter
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
})

function mountQueries(seed = true) {
  if (seed) {
    client.setQueryData(pageKey(), emptyPage())
    client.setQueryData(countKey(), 0)
  }
  wrapper = mount(defineComponent({ setup() {
    const hooks = useNotification()
    list = hooks.useNotifications(ref({ page: 0, size: 20 }))
    count = hooks.useUnreadCount()
    markAll = hooks.useMarkAllAsRead()
    markOne = hooks.useMarkAsRead()
    return () => null
  } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
}

function respondReads(items: ReturnType<typeof event>[], unread: number) {
  requests.filter(request => request.config.method === 'get').forEach(request => {
    request.response.resolve(request.config.url === '/notifications'
      ? pageResponse(items, { size: 20 }) : unread)
  })
}

describe('notifications GET and SSE with real Vue Query and Axios', () => {
  it.each([true, false])('keeps an event received during %s cached reads and completes initial loading', async seed => {
    mountQueries(seed)
    if (seed) void list.refetch()
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(7), recent, 1)
    respondReads([], 0)
    await vi.waitFor(() => expect(requests).toHaveLength(4))
    expect(client.getQueryData(countKey())).toBe(1)
    expect(list.data.value?.content[0]?.notificationId).toBe(seed ? 7 : undefined)
    expect(requests[2]!.config.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
    respondReads([event(7)], 1)
    await vi.waitFor(() => expect(list.isFetching.value || count.isFetching.value).toBe(false))
    expect(list.data.value?.content.map(item => item.notificationId)).toEqual([7])
    expect(count.data.value).toBe(1)
    expect(list.isSuccess.value).toBe(true)
  })

  it('coalesces simultaneous and duplicate events, then rechecks a newer group during reconciliation', async () => {
    mountQueries()
    void list.refetch()
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    for (const incoming of [event(7), event(8), event(7, 2), event(8)]) {
      applyIncomingNotificationToCache(client, incoming, recent, 1)
    }
    expect(client.getQueryData(countKey())).toBe(2)
    respondReads([], 0)
    await vi.waitFor(() => expect(requests).toHaveLength(4))
    applyIncomingNotificationToCache(client, event(7, 3), recent, 1)
    respondReads([event(7, 2), event(8)], 2)
    await vi.waitFor(() => expect(requests).toHaveLength(6))
    applyIncomingNotificationToCache(client, event(7, 3), recent, 1)
    respondReads([event(7, 3), event(8)], 2)
    await vi.waitFor(() => expect(list.isFetching.value || count.isFetching.value).toBe(false))
    expect(requests).toHaveLength(6)
    expect(list.data.value?.content[0]?.groupCount).toBe(3)
    expect(count.data.value).toBe(2)
  })

  it.each([
    ['session', true], ['unmount', true], ['session', false], ['unmount', false],
  ] as const)('stops reconciliation on %s (cached=%s)', async (boundary, seed) => {
    mountQueries(seed)
    if (seed) void list.refetch()
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(7), recent, 1)
    respondReads([], 0)
    await vi.waitFor(() => expect(requests).toHaveLength(4))
    if (boundary === 'session') {
      auth.sessionGeneration = 2
      auth.user = { userId: 2 }
      notifyAuthSessionBoundary(2)
      client.setQueryData(pageKey(2), emptyPage())
      client.setQueryData(countKey(2), 0)
    } else wrapper!.unmount()
    respondReads([event(7)], 1)
    await flushPromises()
    expect(requests).toHaveLength(4)
    expect(requests.slice(2).every(request => request.config.signal?.aborted)).toBe(true)
    if (boundary === 'unmount') applyIncomingNotificationToCache(client, event(8), recent, 1)
    expect(client.getQueryData(countKey(2))).toBe(boundary === 'session' ? 0 : undefined)
    expect(client.getQueryData(pageKey(2))).toEqual(boundary === 'session' ? emptyPage() : undefined)
    expect(client.getQueryData(countKey())).toBe(boundary === 'unmount' ? 2 : 1)
  })

  it('does not repeat a current-session read for events from another session', async () => {
    mountQueries()
    void list.refetch()
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(9), recent, 2)
    respondReads([], 0)
    await vi.waitFor(() => expect(list.isFetching.value || count.isFetching.value).toBe(false))
    expect(requests).toHaveLength(2)
  })

  it('finishes an initial authoritative count even after two SSE events create a synthetic cache', async () => {
    mountQueries(false)
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(7), recent, 1)
    applyIncomingNotificationToCache(client, event(8), recent, 1)
    expect(client.getQueryData(countKey())).toBe(2)
    respondReads([], 100)
    await vi.waitFor(() => expect(requests).toHaveLength(4))
    respondReads([event(8), event(7)], 102)
    await vi.waitFor(() => expect(count.isFetching.value).toBe(false))
    expect(count.data.value).toBe(102)
  })

  it.each([true, false])('keeps successful reads pending through five events without replacing existing UI with an error (cached=%s)', async seed => {
    mountQueries(seed)
    if (seed) void list.refetch()
    for (let index = 0; index < 5; index += 1) {
      await vi.waitFor(() => expect(requests).toHaveLength((index + 1) * 2))
      applyIncomingNotificationToCache(client, event(7, index + 1), recent, 1)
      respondReads([], 0)
      await flushPromises()
      expect(list.isError.value || count.isError.value).toBe(false)
      expect(list.isLoading.value).toBe(!seed)
      expect(list.data.value?.content[0]?.groupCount).toBe(seed ? index + 1 : undefined)
    }
    await vi.waitFor(() => expect(requests).toHaveLength(12))
    respondReads([event(7, 5)], 1)
    await vi.waitFor(() => expect(list.isFetching.value || count.isFetching.value).toBe(false))
    expect(list.isSuccess.value && count.isSuccess.value).toBe(true)
    expect(count.data.value).toBe(1)
  })

  it('propagates an actual read failure without repeatedly retrying it inside the request', async () => {
    mountQueries(false)
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(7), recent, 1)
    requests.forEach(request => request.response.reject(new Error('Network failure')))
    await vi.waitFor(() => expect(list.isError.value && count.isError.value).toBe(true))
    expect(requests).toHaveLength(2)
  })

  it.each([
    ['all', true], ['one', true], ['all', false], ['one', false],
  ] as const)('accepts mark-%s during reconciliation (cached=%s)', async (action, seed) => {
    mountQueries(seed)
    if (seed) void list.refetch()
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    applyIncomingNotificationToCache(client, event(7), recent, 1)
    respondReads([], 0)
    await vi.waitFor(() => expect(requests).toHaveLength(4))
    const pending = action === 'all' ? markAll.mutateAsync() : markOne.mutateAsync(7)
    await vi.waitFor(() => expect(requests).toHaveLength(5))
    requests[4]!.response.resolve(undefined)
    await pending
    await vi.waitFor(() => expect(requests).toHaveLength(seed ? 7 : 6))
    // An initial page still has no cache: invalidation keeps its current GET.
    // That pre-mutation snapshot must not restore the now-read notification.
    requests.slice(2, 4).forEach(request => request.response.resolve(request.config.url === '/notifications'
      ? pageResponse([event(7)], { size: 20 }) : 1))
    await vi.waitFor(() => expect(requests).toHaveLength(7))
    respondReads([{ ...event(7), isRead: true }], 0)
    await vi.waitFor(() => expect(list.isFetching.value || count.isFetching.value).toBe(false))
    expect(list.data.value?.content[0]?.isRead).toBe(true)
    expect(count.data.value).toBe(0)
  })
})
