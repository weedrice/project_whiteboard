import { describe, expect, it, vi } from 'vitest'
import { createDeferred } from '@/test/async'
import { createMentionCandidateLookup } from '../useMentionAutocomplete'
import { effectScope } from 'vue'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { useMentionAutocomplete } from '../useMentionAutocomplete'

describe('createMentionCandidateLookup', () => {
  it('aborts the previous lookup and only returns the latest response', async () => {
    const first = createDeferred<Array<{ userId: number, displayName: string, profileImageUrl: null }>>()
    const second = createDeferred<Array<{ userId: number, displayName: string, profileImageUrl: null }>>()
    const signals: AbortSignal[] = []
    const load = vi.fn((_query: string, signal: AbortSignal) => {
      signals.push(signal)
      return signals.length === 1 ? first.promise : second.promise
    })
    const lookup = createMentionCandidateLookup(load, () => 1)

    const firstSearch = lookup.search('a')
    const secondSearch = lookup.search('al')
    expect(signals[0].aborted).toBe(true)

    second.resolve([{ userId: 2, displayName: 'Alice', profileImageUrl: null }])
    first.resolve([{ userId: 1, displayName: 'Old', profileImageUrl: null }])

    await expect(secondSearch).resolves.toEqual([
      { userId: 2, displayName: 'Alice', profileImageUrl: null },
    ])
    await expect(firstSearch).resolves.toEqual([])
  })

  it('discards a response resolved after the session generation changes', async () => {
    let generation = 3
    const deferred = createDeferred<Array<{ userId: number, displayName: string, profileImageUrl: null }>>()
    const lookup = createMentionCandidateLookup(() => deferred.promise, () => generation)

    const pending = lookup.search('alice')
    generation = 4
    deferred.resolve([{ userId: 1, displayName: 'Alice', profileImageUrl: null }])

    await expect(pending).resolves.toEqual([])
  })

  it('normalizes an aborted loader rejection to an empty result', async () => {
    const load = vi.fn((_query: string, signal: AbortSignal) => new Promise<never>((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true })
    }))
    const lookup = createMentionCandidateLookup(load, () => 1)

    const firstSearch = lookup.search('a')
    const secondSearch = lookup.search('al')

    await expect(firstSearch).resolves.toEqual([])
    lookup.cancel()
    await expect(secondSearch).resolves.toEqual([])
  })

  it('exposes a real lookup failure and retries the last query', async () => {
    const load = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([{ userId: 1, displayName: 'Alice', profileImageUrl: null }])
    const lookup = createMentionCandidateLookup(load, () => 1)

    await expect(lookup.search('alice')).rejects.toThrow('offline')
    expect(lookup.error.value).toBeInstanceOf(Error)

    await expect(lookup.retry()).resolves.toEqual([
      { userId: 1, displayName: 'Alice', profileImageUrl: null },
    ])
    expect(load).toHaveBeenLastCalledWith('alice', expect.any(AbortSignal))
    expect(lookup.error.value).toBeNull()
  })

  it('keeps mention lookup failures visible until retry succeeds', async () => {
    const search = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([{ userId: 1, displayName: 'Alice', profileImageUrl: null }])
    const autocomplete = useMentionAutocomplete({
      resolveRange: () => ({ query: 'alice', start: 0, end: 6 }),
      onSelect: vi.fn(),
      search,
    })

    await autocomplete.refresh()
    expect(autocomplete.error.value).toBeInstanceOf(Error)
    expect(autocomplete.items.value).toEqual([])

    await autocomplete.retry()
    expect(autocomplete.error.value).toBeNull()
    expect(autocomplete.isOpen.value).toBe(true)
  })

  it('aborts pending lookup and closes visible candidates at the session boundary', async () => {
    const deferred = createDeferred<Array<{ userId: number, displayName: string, profileImageUrl: null }>>()
    let requestSignal: AbortSignal | undefined
    const scope = effectScope()
    const autocomplete = scope.run(() => useMentionAutocomplete({
      resolveRange: () => ({ query: 'alice', start: 0, end: 6 }),
      onSelect: vi.fn(),
      search: (_query, signal) => {
        requestSignal = signal
        return deferred.promise
      },
    }))!

    const pending = autocomplete.refresh()
    notifyAuthSessionBoundary(9)

    expect(requestSignal?.aborted).toBe(true)
    expect(autocomplete.isOpen.value).toBe(false)
    expect(autocomplete.items.value).toEqual([])

    deferred.resolve([{ userId: 1, displayName: 'Alice', profileImageUrl: null }])
    await pending
    expect(autocomplete.items.value).toEqual([])
    scope.stop()
  })
})
