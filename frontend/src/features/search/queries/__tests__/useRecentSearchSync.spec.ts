import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useSearch } from '../useSearch'
import { useRecentSearchSync } from '../useRecentSearchSync'
import { installRecentSearchStorageCleanup, readRecentSearchItems, recentSearchStorageKey,
  removeRecentSearchItems, writeRecentSearchItems } from '../../recentSearchStorage'
import { searchApi } from '@/api/search'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { searchQueryKeys } from '../searchQueryKeys'
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
let stopStorageCleanup: () => void

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
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity, staleTime: 30_000 } } })
  clients.push(client)
  const params = ref<SearchParams>({ q: initialKeyword })
  let sync!: ReturnType<typeof useRecentSearchSync>
  let recent!: ReturnType<ReturnType<typeof useSearch>['useRecentSearches']>
  wrappers.push(mount(defineComponent({ setup() {
    const searches = useSearch()
    recent = searches.useRecentSearches(computed(() => auth.isAuthenticated && auth.user?.userId != null))
    sync = useRecentSearchSync({ data: recent.data, refetch: recent.refetch })
    searches.useIntegratedSearch(params, sync.beginSearch)
    return () => null
  } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))
  return { client, params, sync, recent }
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  localStorage.clear()
  ;[null, 10, 20].forEach(removeRecentSearchItems)
  auth = makeAuth()
  stopStorageCleanup = installRecentSearchStorageCleanup(auth)
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
  stopStorageCleanup()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('recent-search synchronization with real Vue Query', () => {
  it('records a repeated guest search ahead of newer terms even while its cached results are fresh', async () => {
    auth.isAuthenticated = false
    auth.user = null
    const { sync, params } = mountSearch('first')
    await settle()
    await vi.advanceTimersByTimeAsync(10)
    params.value = { q: 'second' }
    await settle()
    expect(sync.items.value.map(item => item.keyword)).toEqual(['second', 'first'])
    await vi.advanceTimersByTimeAsync(10)
    params.value = { q: 'first' }
    await settle()
    expect(sync.items.value.map(item => item.keyword)).toEqual(['first', 'second'])
    expect(readRecentSearchItems(null).map(item => item.keyword)).toEqual(['first', 'second'])
    expect(searchApi.search).toHaveBeenCalledTimes(3)
  })

  it('recreates a deliberately re-searched guest keyword after deletion even with cached results', async () => {
    auth.isAuthenticated = false
    auth.user = null
    const { sync, params } = mountSearch('deleted')
    await settle()
    params.value = { q: 'other' }
    await settle()
    sync.beginDeletion('deleted')()
    expect(sync.items.value.map(item => item.keyword)).toEqual(['other'])
    await vi.advanceTimersByTimeAsync(10)
    params.value = { q: 'deleted' }
    await settle()
    expect(sync.items.value.map(item => item.keyword)).toEqual(['deleted', 'other'])
    expect(searchApi.search).toHaveBeenCalledTimes(3)
    expect(searchApi.getRecentSearches).not.toHaveBeenCalled()
  })

  it('persists guest history across remounts and deletes it without a history API request', async () => {
    auth.isAuthenticated = false
    auth.user = null
    const first = mountSearch('guest term')
    await settle()
    expect(first.sync.items.value).toEqual([{ keyword: 'guest term', searchedAt: expect.any(String) }])
    wrappers.pop()!.unmount()
    const second = mountSearch('')
    await settle()
    expect(second.sync.items.value[0]?.keyword).toBe('guest term')
    second.sync.beginDeletion('guest term')()
    expect(readRecentSearchItems(null)).toEqual([])
    expect(searchApi.getRecentSearches).not.toHaveBeenCalled()
  })

  it('hydrates only the signed-in account then replaces its cache with the authoritative DB snapshot', async () => {
    writeRecentSearchItems(null, [{ keyword: 'guest', searchedAt: '2026-10-27T12:00:00' }])
    writeRecentSearchItems(10, [{ logId: 10, keyword: 'cached', searchedAt: '2026-10-27T12:00:00' }])
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.getRecentSearches>>>()
    vi.mocked(searchApi.getRecentSearches).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('')
    expect(sync.items.value.map(item => item.keyword)).toEqual(['cached'])
    await settle()
    response.resolve(apiSuccessDataResponse<typeof searchApi.getRecentSearches>(historyResponse()))
    await settle()
    expect(sync.items.value).toEqual([])
    expect(readRecentSearchItems(10)).toEqual([])
    expect(readRecentSearchItems(null)[0]?.keyword).toBe('guest')
    expect(searchApi.search).not.toHaveBeenCalled()
  })

  it('keeps guest and account histories isolated through login, account switch and logout', async () => {
    auth.isAuthenticated = false
    auth.user = null
    const { sync, params } = mountSearch('guest')
    await settle()
    params.value = { q: '' }
    serverHistory = [{ logId: 10, keyword: 'account ten', searchedAt: '2026-10-27T12:00:00' }]
    auth.sessionGeneration += 1
    auth.isAuthenticated = true
    auth.user = { userId: 10 }
    notifyAuthSessionBoundary(auth.sessionGeneration)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(searchApi.search).toHaveBeenCalledTimes(1)
    serverHistory = [{ logId: 20, keyword: 'account twenty', searchedAt: '2026-10-27T13:00:00' }]
    auth.sessionGeneration += 1
    auth.user = { userId: 20 }
    notifyAuthSessionBoundary(auth.sessionGeneration)
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(localStorage.getItem(recentSearchStorageKey(10))).toBeNull()
    wrappers.pop()!.unmount()
    auth.sessionGeneration += 1
    auth.isAuthenticated = false
    auth.user = null
    notifyAuthSessionBoundary(auth.sessionGeneration)
    expect(localStorage.getItem(recentSearchStorageKey(20))).toBeNull()
    const guest = mountSearch('')
    await settle()
    expect(guest.sync.items.value.map(item => item.keyword)).toEqual(['guest'])
  })

  it('retains account storage through bootstrap generation changes and ordinary token rotation', () => {
    const cached = [{ logId: 10, keyword: 'cache', searchedAt: '2026-10-27T12:00:00' }]
    writeRecentSearchItems(10, cached)
    auth.sessionGeneration += 1
    auth.accessToken = 'rotated'
    expect(readRecentSearchItems(10)).toEqual(cached)
  })

  it('never displays guest history while authenticated user hydration is pending', async () => {
    writeRecentSearchItems(null, [{ keyword: 'guest', searchedAt: '2026-10-27T12:00:00' }])
    auth.user = null
    const { sync } = mountSearch('')
    await settle()
    expect(sync.items.value).toEqual([])
    expect(readRecentSearchItems(null)[0]?.keyword).toBe('guest')
  })

  it('clears mounted guest chips when token installation precedes delayed user hydration', async () => {
    auth.isAuthenticated = false
    auth.user = null
    writeRecentSearchItems(null, [{ keyword: 'guest', searchedAt: '2026-10-27T12:00:00' }])
    const accountCache = [{ logId: 10, keyword: 'account cache', searchedAt: '2026-10-27T12:00:00' }]
    writeRecentSearchItems(10, accountCache)
    const { sync } = mountSearch('')
    await settle()
    expect(sync.items.value.map(item => item.keyword)).toEqual(['guest'])
    // Real setTokens/applyNewSession order: boundary, token, then async user hydration.
    auth.sessionGeneration += 1
    notifyAuthSessionBoundary(auth.sessionGeneration)
    auth.isAuthenticated = true
    expect(sync.items.value).toEqual([])
    await settle()
    expect(sync.items.value).toEqual([])
    expect(searchApi.getRecentSearches).not.toHaveBeenCalled()
    serverHistory = [{ logId: 11, keyword: 'confirmed account', searchedAt: '2026-10-27T13:00:00' }]
    auth.user = { userId: 10 }
    expect(sync.items.value).toEqual(accountCache)
    await settle()
    expect(searchApi.getRecentSearches).toHaveBeenCalledOnce()
    expect(sync.items.value).toEqual(serverHistory)
    expect(readRecentSearchItems(null)[0]?.keyword).toBe('guest')
    expect(searchApi.search).not.toHaveBeenCalled()
  })

  it('does not restore the departed account cache from a late history response', async () => {
    writeRecentSearchItems(10, [{ logId: 10, keyword: 'private ten', searchedAt: '2026-10-27T12:00:00' }])
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.getRecentSearches>>>()
    vi.mocked(searchApi.getRecentSearches).mockImplementationOnce(() => response.promise)
    const oldHistory = { ...historyResponse(), content: readRecentSearchItems(10) as RecentSearchLog[] }
    const { sync } = mountSearch('')
    await settle()
    serverHistory = [{ logId: 20, keyword: 'twenty', searchedAt: '2026-10-27T13:00:00' }]
    auth.sessionGeneration += 1
    auth.user = { userId: 20 }
    notifyAuthSessionBoundary(auth.sessionGeneration)
    await settle()
    response.resolve(apiSuccessDataResponse<typeof searchApi.getRecentSearches>(oldHistory))
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(readRecentSearchItems(20)).toEqual(serverHistory)
    expect(localStorage.getItem(recentSearchStorageKey(10))).toBeNull()
  })

  it('ignores corrupt storage and retains guest history in memory when browser storage is denied', async () => {
    auth.isAuthenticated = false
    auth.user = null
    localStorage.setItem(recentSearchStorageKey(null), '{broken json')
    const { sync } = mountSearch('')
    await settle()
    expect(sync.items.value).toEqual([])
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
    sync.beginSearch('memory')('memory')
    wrappers.pop()!.unmount()
    const next = mountSearch('')
    await settle()
    expect(next.sync.items.value.map(item => item.keyword)).toEqual(['memory'])
    next.sync.beginDeletion()()
    expect(next.sync.items.value).toEqual([])
  })

  it('does not restore a deleted guest keyword when its old successful search arrives late', async () => {
    auth.isAuthenticated = false
    auth.user = null
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.search>>>()
    vi.mocked(searchApi.search).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('late guest')
    await settle()
    sync.beginDeletion()()
    response.resolve(searchResponse('late guest'))
    await settle()
    expect(sync.items.value).toEqual([])
    expect(readRecentSearchItems(null)).toEqual([])
  })
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

  it('clears server rows first discovered while a cached-history clear request is pending', async () => {
    writeRecentSearchItems(10, [{ logId: 1, keyword: 'cached', searchedAt: '2026-10-27T12:00:00' }])
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.getRecentSearches>>>()
    vi.mocked(searchApi.getRecentSearches).mockImplementationOnce(() => response.promise)
    const { sync } = mountSearch('')
    await settle()
    const completeDeletion = sync.beginDeletion()
    serverHistory = [
      { logId: 2, keyword: 'previously unknown', searchedAt: '2026-10-27T13:00:00' },
      { logId: 1, keyword: 'cached', searchedAt: '2026-10-27T12:00:00' },
    ]
    response.resolve(apiSuccessDataResponse<typeof searchApi.getRecentSearches>(historyResponse()))
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    completeDeletion()
    expect(sync.items.value).toEqual([])
    expect(readRecentSearchItems(10)).toEqual([])
  })

  it('cancels a pre-clear history snapshot before deletion completion and accepts the next authoritative read', async () => {
    writeRecentSearchItems(10, [{ logId: 1, keyword: 'cached', searchedAt: '2026-10-27T12:00:00' }])
    const response = createDeferred<Awaited<ReturnType<typeof searchApi.getRecentSearches>>>()
    vi.mocked(searchApi.getRecentSearches).mockImplementationOnce(() => response.promise)
    const { sync, client } = mountSearch('')
    await settle()
    const completeDeletion = sync.beginDeletion()
    const queryKey = sessionQueryKey(auth.sessionGeneration, searchQueryKeys.recent)
    await client.cancelQueries({ queryKey })
    completeDeletion()
    response.resolve(apiSuccessDataResponse<typeof searchApi.getRecentSearches>({ ...historyResponse(),
      content: [{ logId: 2, keyword: 'old unknown', searchedAt: '2026-10-27T13:00:00' }] }))
    const refresh = client.invalidateQueries({ queryKey })
    await settle()
    await refresh
    expect(sync.items.value).toEqual([])
    expect(readRecentSearchItems(10)).toEqual([])
    const completeSearch = sync.beginSearch('new after clear')
    serverHistory = [{ logId: 3, keyword: 'new after clear', searchedAt: '2026-10-27T14:00:00' }]
    completeSearch('new after clear')
    await settle()
    expect(sync.items.value).toEqual(serverHistory)
    expect(sync.isReconciling.value).toBe(false)
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
