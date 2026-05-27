import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { usePaginatedQueryState } from '../usePaginatedQueryState'

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
})
