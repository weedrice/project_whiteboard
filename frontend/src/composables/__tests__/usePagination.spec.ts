import { describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { usePagination, type PaginationParams } from '../usePagination'
import type { ApiResponse, PageResponse } from '@/types'

const pageResponse = <T>(content: T[], params: { page: number; size: number; totalPages?: number }): ApiResponse<PageResponse<T>> => ({
    success: true,
    data: {
        content,
        totalElements: content.length,
        totalPages: params.totalPages ?? 1,
        size: params.size,
        number: params.page,
        first: params.page === 0,
        last: params.page >= (params.totalPages ?? 1) - 1,
        empty: content.length === 0,
    },
})

function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return { promise, resolve, reject }
}

describe('usePagination', () => {
    it('fetches items with current page and size', async () => {
        const scope = effectScope()
        const fetchFn = vi.fn(async (params: PaginationParams) => (
            pageResponse([{ id: 1 }], { page: Number(params.page), size: Number(params.size), totalPages: 2 })
        ))

        try {
            await scope.run(async () => {
                const pagination = usePagination(fetchFn, { page: 0, size: 15 })

                await pagination.fetch()

                expect(fetchFn).toHaveBeenCalledWith({ page: 0, size: 15 })
                expect(pagination.items.value).toEqual([{ id: 1 }])
                expect(pagination.totalPages.value).toBe(2)
                expect(pagination.loading.value).toBe(false)
                expect(pagination.error.value).toBeNull()
            })
        } finally {
            scope.stop()
        }
    })

    it('updates page and size through handlers', async () => {
        const scope = effectScope()
        const fetchFn = vi.fn(async (params: PaginationParams) => (
            pageResponse([{ id: Number(params.page) }], { page: Number(params.page), size: Number(params.size) })
        ))

        try {
            await scope.run(async () => {
                const pagination = usePagination(fetchFn, { page: 0, size: 15 })

                await pagination.handlePageChange(2)
                expect(fetchFn).toHaveBeenLastCalledWith({ page: 2, size: 15 })
                expect(pagination.page.value).toBe(2)

                pagination.size.value = 30
                await pagination.handleSizeChange()
                expect(fetchFn).toHaveBeenLastCalledWith({ page: 0, size: 30 })
                expect(pagination.page.value).toBe(0)
            })
        } finally {
            scope.stop()
        }
    })

    it('keeps the latest fetch result when requests resolve out of order', async () => {
        const scope = effectScope()
        const firstRequest = deferred<ApiResponse<PageResponse<{ id: number }>>>()
        const secondRequest = deferred<ApiResponse<PageResponse<{ id: number }>>>()
        const fetchFn = vi
            .fn<(params: PaginationParams) => Promise<ApiResponse<PageResponse<{ id: number }>>>>()
            .mockReturnValueOnce(firstRequest.promise)
            .mockReturnValueOnce(secondRequest.promise)

        try {
            await scope.run(async () => {
                const pagination = usePagination(fetchFn, { page: 0, size: 15 })

                const firstFetch = pagination.fetch()
                pagination.page.value = 1
                const secondFetch = pagination.fetch()

                secondRequest.resolve(pageResponse([{ id: 2 }], { page: 1, size: 15, totalPages: 3 }))
                await secondFetch

                expect(pagination.items.value).toEqual([{ id: 2 }])
                expect(pagination.totalPages.value).toBe(3)
                expect(pagination.loading.value).toBe(false)

                firstRequest.resolve(pageResponse([{ id: 1 }], { page: 0, size: 15, totalPages: 2 }))
                await firstFetch

                expect(pagination.items.value).toEqual([{ id: 2 }])
                expect(pagination.totalPages.value).toBe(3)
                expect(pagination.loading.value).toBe(false)
            })
        } finally {
            scope.stop()
        }
    })
})
