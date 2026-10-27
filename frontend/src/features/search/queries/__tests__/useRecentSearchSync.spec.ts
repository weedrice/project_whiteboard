import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useSearch } from '../useSearch'
import { useRecentSearchSync } from '../useRecentSearchSync'
import { searchApi } from '@/api/search'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createDeferred } from '@/test/async'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import type { IntegratedSearchResponse, RecentSearchLog, RecentSearchResponse, SearchParams } from '@/types'

let auth: ReturnType<typeof makeAuth>
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/api/search', () => ({
  normalizeSearchParams: (params: SearchParams) => params,
  searchApi: { search: vi.fn(), getRecentSearches: vi.fn() },
}))

const clients: QueryClient[] = []
const wrappers: VueWrapper[] = []
let serverHistory: RecentSearchLog[]

function makeAuth() {
  return reactive({ sessionGeneration: 1, user: { userId: 10 } as { userId: number } | null,
    isAuthenticated: true, accessToken: 'initial' })
}

function historyResponse(): RecentSearchResponse {
  return { content: [...serverHistory], page: 0, size: 8, totalElements: serverHistory.length,
    totalPages: serverHistory.length ? 1 : 0, hasNext: false, hasPrevious: false }
}

function searchResponse(keyword: string) {
  const empty = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
  return apiSuccessDataResponse<typeof searchApi.search, IntegratedSearchResponse>({
    keyword, postResults: empty, commentResults: empty, userResults: empty, boardResults: [],
  })
}

async function settle() {
  await vi.advanceTimersByTimeAsync(0)
  await flushPromises()
  await nextTick()
  await vi.advanceTimersByTimeAsync(0)
}

function mountSearch(initialKeyword = 'vue') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  clients.push(client)
  const params = ref<SearchParams>({ q: initialKeyword })
  let sync!: ReturnType<typeof useRecentSearchSync>
  let recent!: ReturnType<ReturnType<typeof useSearch>['useRecentSearches']>
  wrappers.push(mount(defineComponent({ setup() {
    const searches = useSearch()
    recent = searches.useRecentSearches(computed(() => auth.isAuthenticated))
    sync = useRecentSearchSync({ data: recent.data, refetch: recent.refetch })
    searches.useIntegratedSearch(params, sync.beginSearch)
    return () => null
  } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))
  return { client, params, sync, recent }
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  auth = makeAuth()
  serverHistory = []
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  vi.mocked(searchApi.search).mockImplementation(async params => searchResponse(params.q!))
  vi.mocked(searchApi.getRecentSearches).mockImplementation(async () => (
    apiSuccessDataResponse<typeof searchApi.getRecentSearches>(historyResponse())
  ))
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  clients.splice(0).forEach(client => client.clear())
  configureAuthQueryScope(() => 0)
  vi.useRealTimers()
})

describe('recent-search synchronization with real Vue Query', () => {
  it('refreshes after a completed search and shows only server-confirmed history after delayed persistence', async () => {
    const { sync } = mountSearch()
    await settle()
    expect(sync.items.value).toEqual([])
    expect(sync.isReconciling.value).toBe(true)

    serverHistory = [{ logId: 41, keyword: 'vue', searchedAt: '2026-10-27T12:00:00' }]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
    const calls = vi.mocked(searchApi.getRecentSearches).mock.calls.length
    await vi.advanceTimersByTimeAsync(10_000)
    expect(searchApi.getRecentSearches).toHaveBeenCalledTimes(calls)
  })

  it('waits for a repeated keyword timestamp update before considering history synchronized', async () => {
    serverHistory = [
      { logId: 2, keyword: 'other', searchedAt: '2026-10-27T12:00:00' },
      { logId: 1, keyword: 'Vue', searchedAt: '2026-10-27T11:00:00' },
    ]
    const { sync, params } = mountSearch('')
    await settle()
    params.value = { q: 'vue' }
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(true)
    serverHistory = [
      { logId: 1, keyword: 'vue', searchedAt: '2026-10-27T13:00:00' }, serverHistory[0]!,
    ]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
  })

  it('confirms a history write that becomes visible before the search response completes', async () => {
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    const { sync, recent } = mountSearch('vue')
    await settle()
    serverHistory = [{ logId: 6, keyword: 'vue', searchedAt: '2026-10-27T12:00:00' }]
    const refresh = recent.refetch()
    await settle()
    await refresh
    response.resolve(searchResponse('vue'))
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
    expect(sync.hasSyncError.value).toBe(false)
  })

  it('does not mistake an older matching row loaded during the initial search for refreshed ordering', async () => {
    serverHistory = [
      { logId: 2, keyword: 'other', searchedAt: '2026-10-27T12:00:00' },
      { logId: 1, keyword: 'vue', searchedAt: '2026-10-27T11:00:00' },
    ]
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('vue')
    await settle()
    response.resolve(searchResponse('vue'))
    await settle()
    expect(sync.isReconciling.value).toBe(true)
    expect(sync.items.value[0]!.keyword).toBe('other')
    serverHistory = [
      { logId: 1, keyword: 'vue', searchedAt: '2026-10-27T13:00:00' }, serverHistory[0]!,
    ]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.isReconciling.value).toBe(false)
    expect(sync.items.value[0]!.keyword).toBe('vue')
  })

  it('accepts initial history already headed by the searched term without endless reconciliation', async () => {
    serverHistory = [{ logId: 1, keyword: 'vue', searchedAt: '2026-10-27T11:00:00' }]
    const { sync } = mountSearch('vue')
    await settle()
    expect(sync.isReconciling.value).toBe(false)
    expect(sync.items.value).toEqual(serverHistory)
  })

  it('lets the latest search own ordering when two writes become visible between polls', async () => {
    serverHistory = [
      { logId: 3, keyword: 'other', searchedAt: '2026-10-27T12:00:00' },
      { logId: 1, keyword: 'first', searchedAt: '2026-10-27T11:00:00' },
    ]
    const { sync, params } = mountSearch('first')
    await settle()
    expect(sync.isReconciling.value).toBe(true)
    params.value = { q: 'second' }
    await settle()
    serverHistory = [
      { logId: 2, keyword: 'second', searchedAt: '2026-10-27T14:00:00' },
      { logId: 1, keyword: 'first', searchedAt: '2026-10-27T13:00:00' },
      serverHistory[0]!,
    ]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
    await vi.advanceTimersByTimeAsync(10_000)
    expect(sync.hasSyncError.value).toBe(false)
  })

  it('does not promote an older search whose response arrives after a newer search', async () => {
    const oldResponse = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => oldResponse.promise)
    const { sync, params } = mountSearch('old')
    await settle()
    params.value = { q: 'new' }
    await settle()
    oldResponse.resolve(searchResponse('old'))
    await settle()
    expect(sync.items.value).toEqual([])
    serverHistory = [{ logId: 9, keyword: 'new', searchedAt: '2026-10-27T13:00:00' }]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
    expect(vi.mocked(searchApi.search).mock.calls[0]![1]?.signal?.aborted).toBe(true)
  })

  it.each([20, null])('clears pending history and ignores old responses after session changes to %s', async userId => {
    const oldResponse = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    const { sync, params } = mountSearch('first')
    await settle()
    vi.mocked(searchApi.search).mockImplementationOnce(() => oldResponse.promise)
    params.value = { q: 'late' }
    await settle()
    params.value = { q: '' }
    auth.sessionGeneration += 1
    auth.user = userId === null ? null : { userId }
    auth.isAuthenticated = userId !== null
    notifyAuthSessionBoundary(auth.sessionGeneration)
    await settle()
    oldResponse.resolve(searchResponse('late'))
    await settle()
    expect(sync.items.value).toEqual([])
    expect(sync.isReconciling.value).toBe(false)
  })

  it('preserves reconciliation through ordinary same-session token rotation', async () => {
    const { sync } = mountSearch()
    await settle()
    auth.accessToken = 'rotated'
    serverHistory = [{ logId: 3, keyword: 'vue', searchedAt: '2026-10-27T12:00:00' }]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
  })

  it('prevents a delayed pre-clear search from restoring deleted history, but allows a later deliberate search', async () => {
    const oldResponse = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => oldResponse.promise)
    const { sync, recent, params } = mountSearch('old')
    await settle()
    sync.beginDeletion()()
    oldResponse.resolve(searchResponse('old'))
    await settle()
    serverHistory = [{ logId: 4, keyword: 'old', searchedAt: '2026-10-27T12:00:00' }]
    const refresh = recent.refetch()
    await settle()
    await refresh
    expect(sync.items.value).toEqual([])
    params.value = { q: 'old', page: 1 }
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(true)
  })

  it('keeps a search started after clear was requested when the delete completes later', async () => {
    const { sync, params } = mountSearch('')
    await settle()
    const completeDelete = sync.beginDeletion()
    params.value = { q: 'newer' }
    await settle()
    completeDelete()
    expect(sync.items.value).toEqual([])
    expect(sync.isReconciling.value).toBe(true)
  })

  it('continues a different pending search after deleting one saved keyword', async () => {
    serverHistory = [{ logId: 1, keyword: 'removed', searchedAt: '2026-10-27T11:00:00' }]
    const { sync, params } = mountSearch('')
    await settle()
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    params.value = { q: 'kept' }
    await settle()
    const completeDelete = sync.beginDeletion('removed')
    serverHistory = []
    completeDelete()
    response.resolve(searchResponse('kept'))
    await settle()
    expect(sync.isReconciling.value).toBe(true)
    serverHistory = [{ logId: 2, keyword: 'kept', searchedAt: '2026-10-27T12:00:00' }]
    await vi.advanceTimersByTimeAsync(200)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
  })

  it('still discards a pending response for the individually deleted keyword', async () => {
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('removed')
    await settle()
    sync.beginDeletion('removed')()
    response.resolve(searchResponse('removed'))
    await settle()
    expect(sync.isReconciling.value).toBe(false)
    expect(sync.items.value).toEqual([])
  })

  it('does not suppress search completion when deletion fails', async () => {
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('kept')
    await settle()
    sync.beginDeletion() // No success callback: the destructive request failed.
    response.resolve(searchResponse('kept'))
    await settle()
    expect(sync.items.value).toEqual([])
    expect(sync.isReconciling.value).toBe(true)
  })

  it('keeps the server list with explicit retry after bounded reconciliation, and stops on disposal', async () => {
    const { sync, params } = mountSearch()
    await settle()
    await vi.advanceTimersByTimeAsync(7700)
    await settle()
    expect(sync.hasSyncError.value).toBe(true)
    expect(sync.items.value).toEqual([])
    serverHistory = [{ logId: 5, keyword: 'vue', searchedAt: '2026-10-27T12:00:00' }]
    sync.retry()
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.hasSyncError.value).toBe(false)
    params.value = { q: 'pending at disposal' }
    await settle()
    expect(sync.isReconciling.value).toBe(true)
    wrappers.pop()!.unmount()
    const calls = vi.mocked(searchApi.getRecentSearches).mock.calls.length
    await vi.advanceTimersByTimeAsync(20_000)
    expect(searchApi.getRecentSearches).toHaveBeenCalledTimes(calls)
  })
})
