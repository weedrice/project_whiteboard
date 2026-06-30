import { computed, type ComputedRef, type Ref } from 'vue'
import type { PageResponse } from '@/types'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'

type Translate = (key: string) => string
type ErrorMessageResolver = (error: unknown, t: Translate) => string

const defaultTranslate: Translate = (key) => key

type PaginationParams = {
  page: number
  size: number
}

type PageDataRef<T> = Ref<PageResponse<T> | null | undefined> | ComputedRef<PageResponse<T> | null | undefined>

type PaginatedQueryResult<T> = {
  data: PageDataRef<T>
  isError: Ref<boolean> | ComputedRef<boolean>
  isLoading: Ref<boolean> | ComputedRef<boolean>
  error: Ref<unknown> | ComputedRef<unknown>
  refetch: () => unknown
}

interface PaginatedListStateOptions {
  initialPage?: number
  initialSize?: number
  t?: Translate
  getErrorMessage?: ErrorMessageResolver
}

export function usePaginatedListState<
  T,
  TResult extends PaginatedQueryResult<T> = PaginatedQueryResult<T>,
>(
  usePaginatedQuery: (params: Ref<PaginationParams>) => TResult,
  options: PaginatedListStateOptions = {},
) {
  const pagination = usePaginatedQueryState({
    initialPage: options.initialPage,
    initialSize: options.initialSize,
  })
  const query = usePaginatedQuery(pagination.params)
  const pageState = usePageResponseState(query.data, pagination.page)
  const errorMessage = computed(() => {
    if (!query.isError.value) {
      return ''
    }
    if (options.getErrorMessage) {
      return options.getErrorMessage(query.error.value, options.t ?? defaultTranslate)
    }
    return getListLoadErrorMessage(options.t)
  })

  return {
    ...pagination,
    ...query,
    ...pageState,
    errorMessage,
  }
}
