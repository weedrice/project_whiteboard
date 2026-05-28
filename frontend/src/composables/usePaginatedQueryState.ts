import { computed, ref, type ComputedRef, type Ref } from 'vue'
import type { PageResponse } from '@/types'

export interface PaginatedQueryStateOptions<TExtraParams extends Record<string, unknown> = Record<string, unknown>> {
  initialPage?: number
  initialSize?: number
  extraParams?: Ref<TExtraParams> | ComputedRef<TExtraParams>
}

export function usePaginatedQueryState<TExtraParams extends Record<string, unknown> = Record<string, unknown>>(
  options: PaginatedQueryStateOptions<TExtraParams> = {},
) {
  const page = ref(options.initialPage ?? 0)
  const size = ref(options.initialSize ?? 20)

  const params = computed(() => ({
    page: page.value,
    size: size.value,
    ...(options.extraParams?.value ?? {}),
  }))

  function handlePageChange(nextPage: number) {
    page.value = nextPage
  }

  function handleSizeChange(nextSize = size.value) {
    size.value = nextSize
    page.value = 0
  }

  function resetPage() {
    page.value = options.initialPage ?? 0
  }

  return {
    page,
    size,
    params,
    handlePageChange,
    handleSizeChange,
    resetPage,
  }
}

export function usePageResponseState<T>(
  pageData: Ref<PageResponse<T> | null | undefined> | ComputedRef<PageResponse<T> | null | undefined>,
  fallbackPage: Ref<number> | ComputedRef<number>,
) {
  const items = computed(() => pageData.value?.content ?? [])
  const totalPages = computed(() => pageData.value?.totalPages ?? 0)
  const totalElements = computed(() => pageData.value?.totalElements ?? 0)
  const currentPage = computed(() => pageData.value?.number ?? fallbackPage.value)

  return {
    items,
    totalPages,
    totalElements,
    currentPage,
  }
}
