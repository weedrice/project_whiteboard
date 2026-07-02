import { ref, type Ref } from 'vue'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import type { ApiResponse, PageResponse } from '@/types'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import logger from '@/utils/logger'

type Translate = (key: string) => string

export interface DashboardPaginationParams {
  page?: number
  size?: number
  sort?: string
  [key: string]: unknown
}

interface DashboardPaginationFetchContext {
  signal: AbortSignal
}

export function useDashboardPagination<T>(
  fetchFn: (
    params: DashboardPaginationParams,
    context: DashboardPaginationFetchContext,
  ) => Promise<ApiResponse<PageResponse<T>>>,
  initialParams: DashboardPaginationParams,
  t: Translate,
) {
  const page = ref(initialParams.page ?? 0)
  const size = ref(initialParams.size ?? 20)
  const sort = ref<string | undefined>(initialParams.sort)
  const items = ref<T[]>([]) as Ref<T[]>
  const totalCount = ref(0)
  const totalPages = ref(0)
  const failedMessage = getListLoadErrorMessage(t)
  const fetchTask = useLatestAsyncTask<string>({
    getErrorValue: () => failedMessage,
    onError: (err) => logger.error('Failed to fetch paginated data:', err),
  })
  const { loading, error } = fetchTask

  const fetch = async (additionalParams: Record<string, unknown> = {}) => {
    const result = await fetchTask.run(({ signal }) => {
      const params: DashboardPaginationParams = {
        page: page.value,
        size: size.value,
        ...(sort.value && { sort: sort.value }),
        ...additionalParams,
      }

      return fetchFn(params, { signal })
    })

    if (!result) return

    if (result.success) {
      items.value = result.data.content
      totalCount.value = result.data.totalElements
      totalPages.value = result.data.totalPages
    } else {
      error.value = failedMessage
    }
  }

  const handlePageChange = (newPage: number) => {
    page.value = newPage
    return fetch()
  }

  return {
    page,
    size,
    sort,
    items,
    totalCount,
    totalPages,
    loading,
    error,
    fetch,
    handlePageChange,
  }
}
