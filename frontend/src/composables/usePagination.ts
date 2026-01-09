import { ref, type Ref } from 'vue'
import logger from '@/utils/logger'
import type { ApiResponse, PageResponse } from '@/types'

/**
 * 페이지네이션 파라미터 타입
 */
export interface PaginationParams {
    page?: number
    size?: number
    sort?: string
    [key: string]: unknown // 추가 파라미터 허용
}

/**
 * 페이지네이션을 위한 Composable
 * 
 * @example
 * ```typescript
 * const { items, totalCount, page, size, loading, error, fetch, handlePageChange } = usePagination(
 *   async (params) => {
 *     const { data } = await userApi.getMyPosts(params)
 *     return data
 *   },
 *   { page: 0, size: 10 }
 * )
 * 
 * // 초기 로드
 * await fetch()
 * 
 * // 페이지 변경
 * handlePageChange(1)
 * ```
 */
export function usePagination<T>(
    fetchFn: (params: PaginationParams) => Promise<ApiResponse<PageResponse<T>>>,
    initialParams: PaginationParams = { page: 0, size: 20 }
) {
    const page = ref(initialParams.page || 0)
    const size = ref(initialParams.size || 20)
    const sort = ref<string | undefined>(initialParams.sort)
    const items = ref<T[]>([]) as Ref<T[]>
    const totalCount = ref(0)
    const totalPages = ref(0)
    const loading = ref(false)
    const error = ref<string | null>(null)

    /**
     * 데이터 페칭 함수
     * @param additionalParams 추가 파라미터
     */
    const fetch = async (additionalParams: Record<string, unknown> = {}) => {
        loading.value = true
        error.value = null
        
        try {
            const params: PaginationParams = {
                page: page.value,
                size: size.value,
                ...(sort.value && { sort: sort.value }),
                ...additionalParams
            }
            
            const { data } = await fetchFn(params)
            
            if (data.success) {
                items.value = data.data.content
                totalCount.value = data.data.totalElements
                totalPages.value = data.data.totalPages
            } else {
                error.value = 'Failed to load data.'
            }
        } catch (err) {
            logger.error('Failed to fetch paginated data:', err)
            error.value = 'Failed to load data.'
        } finally {
            loading.value = false
        }
    }

    /**
     * 페이지 변경 핸들러
     * @param newPage 새로운 페이지 번호
     */
    const handlePageChange = (newPage: number) => {
        page.value = newPage
        fetch()
    }

    /**
     * 페이지 크기 변경 핸들러
     * @param newSize 새로운 페이지 크기
     */
    const handleSizeChange = (newSize: number) => {
        size.value = newSize
        page.value = 0 // 페이지 크기 변경 시 첫 페이지로 리셋
        fetch()
    }

    /**
     * 정렬 변경 핸들러
     * @param newSort 새로운 정렬 값
     */
    const handleSortChange = (newSort: string) => {
        sort.value = newSort
        page.value = 0 // 정렬 변경 시 첫 페이지로 리셋
        fetch()
    }

    /**
     * 페이지네이션 상태 리셋
     */
    const reset = () => {
        page.value = initialParams.page || 0
        size.value = initialParams.size || 20
        sort.value = initialParams.sort
        items.value = []
        totalCount.value = 0
        totalPages.value = 0
        error.value = null
    }

    return {
        // State
        page,
        size,
        sort,
        items,
        totalCount,
        totalPages,
        loading,
        error,
        
        // Methods
        fetch,
        handlePageChange,
        handleSizeChange,
        handleSortChange,
        reset
    }
}
