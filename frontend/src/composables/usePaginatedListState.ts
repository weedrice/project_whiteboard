import { computed, type ComputedRef, type Ref } from 'vue'
import type { PageResponse } from '@/types'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'

type Translate = (key: string) => string

type PaginationParams = {
  page: number
  size: number
}

type PageDataRef<T> = Ref<PageResponse<T> | null | undefined> | ComputedRef<PageResponse<T> | null | undefined>

type PaginatedQueryResult<T> = {
  data: PageDataRef<T>
  isError: Ref<boolean> | ComputedRef<boolean>
  isLoading: Ref<boolean> | ComputedRef<boolean>
  refetch: () => unknown
}

interface PaginatedListStateOptions {
  initialPage?: number
  initialSize?: number
  t?: Translate
}

export function usePaginatedListState<
  T,
  TParams extends PaginationParams = PaginationParams,
  TResult extends PaginatedQueryResult<T> = PaginatedQueryResult<T>,
>(
  usePaginatedQuery: (params: Ref<TParams>) => TResult,
  options: PaginatedListStateOptions = {},
) {
  const pagination = usePaginatedQueryState({
    initialPage: options.initialPage,
    initialSize: options.initialSize,
  })
  const query = usePaginatedQuery(pagination.params as unknown as Ref<TParams>)
  const pageState = usePageResponseState(query.data, pagination.page)
  const errorMessage = computed(() => query.isError.value ? getListLoadErrorMessage(options.t) : '')

  return {
    ...pagination,
    ...query,
    ...pageState,
    errorMessage,
  }
}
