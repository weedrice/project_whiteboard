import { effectScope, nextTick, ref, watch, type Ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { usePaginatedListState } from '../usePaginatedListState'
import type { PageResponse } from '@/types'

const pageResponse = (number: number, totalPages: number, content: string[]): PageResponse<string> => ({
  content,
  totalElements: content.length,
  totalPages,
  size: 10,
  number,
  first: number === 0,
  last: number >= totalPages - 1,
  empty: content.length === 0,
})

describe('usePaginatedListState', () => {
  it('moves to the last valid page so the reactive query automatically reloads it', async () => {
    const scope = effectScope()
    const data = ref<PageResponse<string> | null>(pageResponse(2, 3, ['last draft']))
    const requestedPages: number[] = []

    const state = scope.run(() => usePaginatedListState(
      (params: Ref<{ page: number; size: number }>) => {
        watch(
          () => params.value.page,
          (page) => requestedPages.push(page),
        )
        return {
          data,
          isError: ref(false),
          isLoading: ref(false),
          error: ref<unknown>(null),
          refetch: vi.fn(),
        }
      },
      { initialPage: 2, initialSize: 10 },
    ))

    data.value = pageResponse(2, 2, [])
    await nextTick()

    expect(state?.page.value).toBe(1)
    expect(state?.params.value.page).toBe(1)
    expect(requestedPages).toEqual([1])

    scope.stop()
  })
})
