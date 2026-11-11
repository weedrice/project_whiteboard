import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import type { RecentSearchResponse } from '@/types'
import {
  readRecentSearchItems, writeRecentSearchItems, recentSearchKeywordKey as keywordKey,
  type RecentSearchItem,
} from '../recentSearchStorage'

const RECONCILIATION_DELAYS = [200, 500, 1000, 2000, 4000]

interface PendingSearch {
  keyword: string
  previousSearchedAt?: string
  hasBaseline: boolean
}

interface RecentSearchSyncOptions {
  data: Ref<RecentSearchResponse | undefined>
  refetch: () => Promise<unknown>
}

/** The search response precedes the asynchronous server-side history write. */
export function useRecentSearchSync({ data, refetch }: RecentSearchSyncOptions) {
  const authStore = useAuthStore()
  const owner = () => authStore.isAuthenticated ? authStore.user?.userId ?? undefined : null
  const hydrate = () => {
    const currentOwner = owner()
    return currentOwner === undefined ? [] : readRecentSearchItems(currentOwner)
  }
  const snapshot = ref<RecentSearchItem[]>(hydrate())
  const pending = ref<PendingSearch[]>([])
  const hasSyncError = ref(false)
  const suppressed = ref(new Set<string>())
  const searchStarts = new Map<string, number>()
  const discardedKeywordSequences = new Map<string, number>()
  let searchSequence = 0
  let discardedSearchSequence = 0
  let reconciliationRevision = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  let disposed = false

  const items = computed(() => snapshot.value
    .filter(item => !suppressed.value.has(keywordKey(item.keyword))))
  const isReconciling = computed(() => pending.value.length > 0)

  function stopReconciliation() {
    reconciliationRevision += 1
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  function reconcile() {
    pending.value = pending.value.filter(item => {
      const index = data.value?.content.findIndex(log => keywordKey(log.keyword) === keywordKey(item.keyword)) ?? -1
      if (index < 0) return true
      // Without an initial snapshot, an old matching row anywhere in the list
      // does not prove it was refreshed. Require the searched term at the front;
      // if it was already first, the visible ordering is already correct.
      return item.hasBaseline
        ? data.value!.content[index]!.searchedAt === item.previousSearchedAt
        : index !== 0
    })
    if (!pending.value.length) {
      hasSyncError.value = false
      stopReconciliation()
    }
  }
  function persistVisibleItems() {
    const currentOwner = owner()
    if (currentOwner !== undefined) snapshot.value = writeRecentSearchItems(currentOwner, items.value)
  }
  watch(data, value => {
    if (!authStore.isAuthenticated || owner() == null || !value) return
    snapshot.value = value.content
    persistVisibleItems()
    reconcile()
  }, { immediate: true })

  function refreshHistory() {
    stopReconciliation()
    if (!pending.value.length) return
    hasSyncError.value = false
    const revision = reconciliationRevision
    const intent = captureAuthSessionIntent(authStore)
    const isCurrent = () => !disposed && revision === reconciliationRevision
      && isAuthSessionIntentCurrent(authStore, intent)

    const attempt = async (index: number) => {
      try {
        await refetch()
      } catch {
        // The recent-search query owns its error state and retry control.
      }
      if (!isCurrent()) return
      reconcile()
      if (!isCurrent()) return
      const delay = RECONCILIATION_DELAYS[index]
      if (delay === undefined) {
        // History recording is best-effort. Keep the server-confirmed list
        // and offer a retry instead of pretending an unpersisted record exists.
        hasSyncError.value = true
        stopReconciliation()
        return
      }
      timer = setTimeout(() => { void attempt(index + 1) }, delay)
    }
    void attempt(0)
  }

  function beginSearch(keyword: string) {
    const intent = captureAuthSessionIntent(authStore)
    const sequence = ++searchSequence
    const hasBaseline = data.value !== undefined
    const previousTimestamps = new Map((data.value?.content ?? [])
      .map(item => [keywordKey(item.keyword), item.searchedAt]))
    searchStarts.set(keywordKey(keyword), sequence)
    return (canonicalKeyword: string) => {
      if (disposed || !isAuthSessionIntentCurrent(authStore, intent)
        || sequence !== searchSequence || sequence <= discardedSearchSequence) return
      const key = keywordKey(canonicalKeyword)
      if (!key) return
      if (sequence <= (discardedKeywordSequences.get(key) ?? 0)) return
      suppressed.value.delete(key)
      if (!authStore.isAuthenticated) {
        snapshot.value = [{ keyword: canonicalKeyword, searchedAt: new Date().toISOString() },
          ...snapshot.value.filter(item => keywordKey(item.keyword) !== key)]
        persistVisibleItems()
        return
      }
      if (owner() == null) return
      // A deliberate repeat may reveal a still-confirmed server row suppressed by deletion.
      if (data.value) snapshot.value = data.value.content
      persistVisibleItems()
      // The latest successful search owns reconciliation of the whole list.
      // An older search can no longer be required to occupy the first position.
      pending.value = [
        { keyword: canonicalKeyword, previousSearchedAt: previousTimestamps.get(key), hasBaseline },
      ]
      refreshHistory()
    }
  }

  function beginDeletion(keyword?: string) {
    const intent = captureAuthSessionIntent(authStore)
    const sequence = searchSequence
    const keys = keyword === undefined
      ? new Set([...searchStarts.keys(), ...snapshot.value.map(item => keywordKey(item.keyword))])
      : new Set([keywordKey(keyword)])
    return () => {
      if (disposed || !isAuthSessionIntentCurrent(authStore, intent)) return
      if (keyword === undefined) {
        discardedSearchSequence = Math.max(discardedSearchSequence, sequence)
        // Cache hydration can finish while DELETE is pending and reveal rows
        // absent from the initial local snapshot. Clear those confirmed rows too.
        for (const item of snapshot.value) keys.add(keywordKey(item.keyword))
      } else {
        const key = keywordKey(keyword)
        discardedKeywordSequences.set(key, Math.max(discardedKeywordSequences.get(key) ?? 0, sequence))
      }
      for (const key of keys) {
        // A deliberate search started after the delete may recreate its history.
        if ((searchStarts.get(key) ?? 0) <= sequence) suppressed.value.add(key)
      }
      pending.value = pending.value.filter(item => !suppressed.value.has(keywordKey(item.keyword)))
      persistVisibleItems()
      if (pending.value.length) refreshHistory()
      else {
        hasSyncError.value = false
        stopReconciliation()
      }
    }
  }

  function reset() {
    stopReconciliation()
    searchSequence += 1
    pending.value = []
    hasSyncError.value = false
    suppressed.value = new Set()
    searchStarts.clear()
    discardedKeywordSequences.clear()
    snapshot.value = hydrate()
  }
  // Token installation can follow the generation boundary while user hydration
  // is still pending. Observe ownership itself so guest chips disappear then.
  watch(owner, reset, { flush: 'sync' })
  useAuthSessionReset(reset, authStore)
  onScopeDispose(() => {
    disposed = true
    reset()
  })

  return { items, isReconciling, hasSyncError, retry: refreshHistory, beginSearch, beginDeletion }
}
