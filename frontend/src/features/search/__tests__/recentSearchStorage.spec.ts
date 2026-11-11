import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizeRecentSearchItems, readRecentSearchItems, recentSearchStorageKey,
  removeRecentSearchItems, writeRecentSearchItems } from '../recentSearchStorage'

beforeEach(() => {
  localStorage.clear()
  removeRecentSearchItems(null)
  removeRecentSearchItems(10)
})
afterEach(() => vi.restoreAllMocks())

describe('recent search browser storage', () => {
  it('sorts newest first, trims, deduplicates case-insensitively, and limits history to twenty', () => {
    const entries = Array.from({ length: 25 }, (_, index) => ({
      keyword: `term ${index}`, searchedAt: new Date(index * 1000).toISOString(),
    }))
    entries.push({ keyword: ' TERM 24 ', searchedAt: new Date(30_000).toISOString() })
    const result = writeRecentSearchItems(null, entries)
    expect(result).toHaveLength(20)
    expect(result[0]?.keyword).toBe('TERM 24')
    expect(result[1]?.keyword).toBe('term 23')
    expect(result.at(-1)?.keyword).toBe('term 5')
    expect(readRecentSearchItems(null)).toEqual(result)
  })

  it('rejects malformed entries and requires confirmed IDs in account caches', () => {
    const entry = { keyword: 'valid', searchedAt: '2026-11-10T00:00:00Z' }
    expect(normalizeRecentSearchItems([
      null, {}, { ...entry, keyword: '  ' }, { ...entry, searchedAt: 'invalid' },
      entry, { ...entry, logId: -1 }, { ...entry, logId: '10' }, { ...entry, logId: 10 },
    ], 10)).toEqual([{ ...entry, logId: 10 }])
    localStorage.setItem(recentSearchStorageKey(10), JSON.stringify({ content: [entry] }))
    expect(readRecentSearchItems(10)).toEqual([])
    expect(normalizeRecentSearchItems([{ ...entry, logId: 10 }], null)).toEqual([entry])
  })

  it('retains the newest in-memory data after a quota failure and persists it when storage recovers', () => {
    const old = [{ keyword: 'old', searchedAt: '2026-11-09T00:00:00Z' }]
    const current = [{ keyword: 'current', searchedAt: '2026-11-10T00:00:00Z' }]
    writeRecentSearchItems(null, old)
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('full', 'QuotaExceededError')
    })
    writeRecentSearchItems(null, current)
    expect(readRecentSearchItems(null)).toEqual(current)
    write.mockRestore()
    expect(readRecentSearchItems(null)).toEqual(current)
    expect(JSON.parse(localStorage.getItem(recentSearchStorageKey(null))!)).toEqual(current)
    localStorage.clear()
    expect(readRecentSearchItems(null)).toEqual([])
  })

  it('does not resurrect a departed account when storage removal temporarily fails', () => {
    writeRecentSearchItems(10, [{ logId: 1, keyword: 'private', searchedAt: '2026-11-10T00:00:00Z' }])
    const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('denied') })
    removeRecentSearchItems(10)
    remove.mockRestore()
    expect(readRecentSearchItems(10)).toEqual([])
  })
})
