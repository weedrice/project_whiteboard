import { watch } from 'vue'
import { Storage } from '@/utils/storage'
import type { AuthSessionIntentSource } from '@/utils/authSessionIntent'

export interface RecentSearchItem {
  keyword: string
  searchedAt: string
  logId?: number
}

export type RecentSearchOwner = string | number | null
const PREFIX = 'whiteboard:recent-searches:v1:'
const memory = new Map<string, RecentSearchItem[]>()
const unpersisted = new Set<string>()

export const recentSearchKeywordKey = (keyword: string) => keyword.trim().toLowerCase()
export const recentSearchStorageKey = (owner: RecentSearchOwner) => (
  `${PREFIX}${owner === null ? 'guest' : `user:${owner}`}`
)

export function normalizeRecentSearchItems(value: unknown, owner: RecentSearchOwner): RecentSearchItem[] {
  if (!Array.isArray(value)) return []
  const valid: RecentSearchItem[] = []
  for (const item of value) {
    if (!item || typeof item !== 'object' || typeof item.keyword !== 'string'
      || !item.keyword.trim() || typeof item.searchedAt !== 'string'
      || !Number.isFinite(Date.parse(item.searchedAt))) continue
    const validId = Number.isSafeInteger(item.logId) && item.logId > 0
    if (owner !== null && !validId) continue
    valid.push({ keyword: item.keyword.trim(), searchedAt: item.searchedAt,
      ...(owner !== null ? { logId: item.logId as number } : {}) })
  }
  valid.sort((a, b) => Date.parse(b.searchedAt) - Date.parse(a.searchedAt))
  const seen = new Set<string>()
  return valid.filter(item => {
    const key = recentSearchKeywordKey(item.keyword)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).slice(0, 20)
}

export function writeRecentSearchItems(owner: RecentSearchOwner, value: unknown) {
  const items = normalizeRecentSearchItems(value, owner)
  const key = recentSearchStorageKey(owner)
  memory.set(key, items)
  if (Storage.set(key, items)) unpersisted.delete(key)
  else unpersisted.add(key)
  return items
}

export function readRecentSearchItems(owner: RecentSearchOwner): RecentSearchItem[] {
  const key = recentSearchStorageKey(owner)
  // Retry a previously denied write before trusting an older disk snapshot.
  if (unpersisted.has(key)) return writeRecentSearchItems(owner, memory.get(key) ?? [])
  let raw: string | null
  try {
    raw = localStorage.getItem(key)
  } catch {
    return memory.get(key) ?? []
  }
  if (raw === null) {
    memory.delete(key)
    return []
  }
  try {
    const items = normalizeRecentSearchItems(JSON.parse(raw), owner)
    memory.set(key, items)
    return items
  } catch {
    return writeRecentSearchItems(owner, [])
  }
}

export function removeRecentSearchItems(owner: RecentSearchOwner) {
  const key = recentSearchStorageKey(owner)
  memory.delete(key)
  if (Storage.remove(key)) unpersisted.delete(key)
  else unpersisted.add(key) // Keep an empty tombstone if an old disk value cannot be removed.
}

/** App-owned cleanup also runs when the search route is not mounted. */
export function installRecentSearchStorageCleanup(source: AuthSessionIntentSource) {
  return watch(() => source.user?.userId ?? null, (current, previous) => {
    if (previous !== null && previous !== current) removeRecentSearchItems(previous)
  }, { flush: 'sync' })
}
