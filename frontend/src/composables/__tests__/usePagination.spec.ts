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
})
