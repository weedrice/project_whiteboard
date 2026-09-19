import { QueryClient, QueryObserver, type QueryKey } from '@tanstack/vue-query'
import { flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { expect, vi } from 'vitest'
import { createDeferred } from '@/test/async'

export async function expectQueriesRefetchedOnce(
  invalidate: (queryClient: QueryClient) => void,
  affectedKeys: readonly QueryKey[],
  unaffectedKeys: readonly QueryKey[],
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  })
  const response = createDeferred<string>()
  const queries = [...affectedKeys, ...unaffectedKeys].map((queryKey, index) => {
    queryClient.setQueryData(queryKey, 'cached')
    const aborted = vi.fn()
    const queryFn = vi.fn(({ signal }: { signal: AbortSignal }) => {
      signal.addEventListener('abort', aborted)
      return response.promise
    })
    const observer = new QueryObserver(queryClient, { queryKey, queryFn })
    const unsubscribe = observer.subscribe(() => {})
    return { queryKey, queryFn, aborted, unsubscribe, affected: index < affectedKeys.length }
  })

  try {
    invalidate(queryClient)
    await nextTick()

    for (const query of queries) {
      expect(query.queryFn).toHaveBeenCalledTimes(query.affected ? 1 : 0)
      expect(query.aborted).not.toHaveBeenCalled()
      expect(queryClient.getQueryState(query.queryKey)?.isInvalidated).toBe(query.affected)
    }

    response.resolve('refreshed')
    await flushPromises()

    for (const query of queries) {
      expect(query.queryFn).toHaveBeenCalledTimes(query.affected ? 1 : 0)
      expect(query.aborted).not.toHaveBeenCalled()
    }
    return queries
      .filter(({ queryKey }) => queryClient.getQueryData(queryKey) === 'refreshed')
      .map(({ queryKey }) => queryKey)
  } finally {
    response.resolve('refreshed')
    queries.forEach(({ unsubscribe }) => unsubscribe())
    queryClient.clear()
  }
}
