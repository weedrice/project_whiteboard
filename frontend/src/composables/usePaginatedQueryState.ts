import { computed, ref, type ComputedRef, type Ref } from 'vue'

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
