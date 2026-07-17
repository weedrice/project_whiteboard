import { describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useDashboardPagination, type DashboardPaginationParams } from '../useDashboardPagination'
import { createDeferred } from '@/test/async'
import { pageResponseFixture } from '@/test/apiResponseFixtures'
import type { ApiResponse, PageResponse } from '@/types'

function createPage<T>(content: T[], pageNumber = 0): PageResponse<T> {
  return pageResponseFixture(content, { number: pageNumber, size: 20 })
}

function successPage<T>(content: T[], page = 0): ApiResponse<PageResponse<T>> {
  return {
    success: true,
    data: createPage(content, page),
  }
}

function failedPage<T>(): ApiResponse<PageResponse<T>> {
  return {
    success: false,
    data: createPage<T>([]),
  }
}

describe('useDashboardPagination', () => {
  it('passes pagination, sort, and additional params to the fetch function', async () => {
    const fetchFn = vi.fn().mockResolvedValue(successPage(['item']))
    const pagination = useDashboardPagination(fetchFn, {
      page: 2,
      size: 50,
      sort: 'createdAt,desc',
    }, (key) => key)

    await pagination.fetch({ status: 'ACTIVE' })

    expect(fetchFn).toHaveBeenCalledWith({
      page: 2,
      size: 50,
      sort: 'createdAt,desc',
      status: 'ACTIVE',
    }, {
      signal: expect.any(AbortSignal),
    })
    expect(pagination.items.value).toEqual(['item'])
    expect(pagination.totalCount.value).toBe(1)
    expect(pagination.totalPages.value).toBe(1)
  })

  it('updates the page before fetching from handlePageChange', async () => {
    const fetchFn = vi.fn().mockResolvedValue(successPage(['page-three'], 3))
    const pagination = useDashboardPagination(fetchFn, { page: 0, size: 20 }, (key) => key)

    await pagination.handlePageChange(3)

    expect(fetchFn).toHaveBeenCalledWith(expect.objectContaining({ page: 3, size: 20 }), expect.any(Object))
    expect(pagination.page.value).toBe(3)
    expect(pagination.items.value).toEqual(['page-three'])
  })

  it('sets the localized error message for unsuccessful API envelopes', async () => {
    const fetchFn = vi.fn().mockResolvedValue(failedPage<string>())
    const pagination = useDashboardPagination(fetchFn, { page: 0 }, (key) => `t:${key}`)

    await pagination.fetch()

    expect(pagination.error.value).toBe('t:common.messages.loadFailed')
  })

  it('keeps only the latest request result', async () => {
    const scope = effectScope()
    const first = createDeferred<ApiResponse<PageResponse<string>>>()
    const second = createDeferred<ApiResponse<PageResponse<string>>>()
    const signals: AbortSignal[] = []
    const calls: DashboardPaginationParams[] = []

    try {
      await scope.run(async () => {
        const fetchFn = vi.fn((params: DashboardPaginationParams, context: { signal: AbortSignal }) => {
          calls.push(params)
          signals.push(context.signal)
          return calls.length === 1 ? first.promise : second.promise
        })
        const pagination = useDashboardPagination(fetchFn, { page: 0 }, (key) => key)

        const firstFetch = pagination.fetch({ query: 'first' })
        const secondFetch = pagination.fetch({ query: 'second' })

        expect(signals[0].aborted).toBe(true)

        second.resolve(successPage(['second']))
        await secondFetch
        expect(pagination.items.value).toEqual(['second'])

        first.resolve(successPage(['first']))
        await firstFetch
        expect(pagination.items.value).toEqual(['second'])
      })
    } finally {
      scope.stop()
    }
  })

  it('does not apply a response when the caller result version changes', async () => {
    const deferred = createDeferred<ApiResponse<PageResponse<string>>>()
    let resultVersion = 1
    const pagination = useDashboardPagination(
      () => deferred.promise,
      { page: 0 },
      (key) => key,
      { getResultVersion: () => resultVersion },
    )

    const request = pagination.fetch()
    resultVersion += 1
    deferred.resolve(successPage(['previous session']))
    await request

    expect(pagination.items.value).toEqual([])
    expect(pagination.totalCount.value).toBe(0)
  })
})
