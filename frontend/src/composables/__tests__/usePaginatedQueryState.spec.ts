import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { usePageResponseState, usePaginatedQueryState } from '../usePaginatedQueryState'
import type { PageResponse } from '@/types'

describe('usePaginatedQueryState', () => {
    it('builds query params from page, size, and extra params', () => {
        const sort = ref('createdAt,desc')
        const state = usePaginatedQueryState({
            initialSize: 20,
            extraParams: computed(() => ({
                sort: sort.value,
            })),
        })

        expect(state.params.value).toEqual({
            page: 0,
            size: 20,
            sort: 'createdAt,desc',
        })

        state.handlePageChange(2)
        sort.value = 'createdAt,asc'

        expect(state.params.value).toEqual({
            page: 2,
            size: 20,
            sort: 'createdAt,asc',
        })
    })

    it('resets page when page size changes', () => {
        const state = usePaginatedQueryState({ initialPage: 3, initialSize: 10 })

        state.handleSizeChange(50)

        expect(state.page.value).toBe(0)
        expect(state.size.value).toBe(50)
        expect(state.params.value).toEqual({
            page: 0,
            size: 50,
        })
    })

    it('clamps an out-of-range page after the server page count shrinks', () => {
        const state = usePaginatedQueryState({ initialPage: 3, initialSize: 10 })

        expect(state.clampPageToTotalPages(3)).toBe(true)
        expect(state.page.value).toBe(2)
        expect(state.params.value.page).toBe(2)
        expect(state.clampPageToTotalPages(3)).toBe(false)

        expect(state.clampPageToTotalPages(0)).toBe(true)
        expect(state.page.value).toBe(0)
    })

    it('projects page response fields with nullish fallbacks', () => {
        const fallbackPage = ref(2)
        const pageData = ref<PageResponse<string> | null>({
            content: ['first'],
            totalElements: 10,
            totalPages: 5,
            size: 20,
            number: 3,
            first: false,
            last: false,
            empty: false,
        })
        const state = usePageResponseState(pageData, fallbackPage)

        expect(state.items.value).toEqual(['first'])
        expect(state.totalElements.value).toBe(10)
        expect(state.totalPages.value).toBe(5)
        expect(state.currentPage.value).toBe(3)

        pageData.value = {
            content: [],
            totalElements: 0,
            totalPages: 0,
            size: 20,
            number: 0,
            first: true,
            last: true,
            empty: true,
        }

        expect(state.items.value).toEqual([])
        expect(state.totalElements.value).toBe(0)
        expect(state.totalPages.value).toBe(0)
        expect(state.currentPage.value).toBe(0)

        pageData.value = null

        expect(state.items.value).toEqual([])
        expect(state.totalElements.value).toBe(0)
        expect(state.totalPages.value).toBe(0)
        expect(state.currentPage.value).toBe(2)
    })
})
