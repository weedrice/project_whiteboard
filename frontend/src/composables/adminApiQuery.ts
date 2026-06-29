import type { QueryKey } from '@tanstack/vue-query'
import type { AxiosResponse } from 'axios'
import type { ComputedRef, Ref } from 'vue'
import type { ApiResponse, PageResponse } from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import {
  useApiPageQuery,
  useApiQuery,
  useNullableApiPageQuery,
  useNullableApiQuery,
} from '@/composables/useApiQuery'

type AdminQueryKey = QueryKey | Ref<QueryKey> | ComputedRef<QueryKey>
type AdminEnabled = Ref<boolean> | ComputedRef<boolean>

export type AdminPageFetcher<T> = () => Promise<AxiosResponse<ApiResponse<PageResponse<T> | PageResponseRaw<T>>>>
export type AdminDataFetcher<T> = () => Promise<AxiosResponse<ApiResponse<T>>>

export function useAdminPageQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: AdminPageFetcher<T>,
  options: { enabled?: AdminEnabled } = {}
) {
  return useApiPageQuery<T>({
    queryKey,
    request: () => fetcher(),
    enabled: options.enabled,
  })
}

export function useAdminNullablePageQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: () => ReturnType<AdminPageFetcher<T>> | null,
  enabled: AdminEnabled
) {
  return useNullableApiPageQuery<T>({
    queryKey,
    request: () => fetcher(),
    enabled,
  })
}

export function useAdminDataQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: AdminDataFetcher<T>
) {
  return useApiQuery<T>({
    queryKey,
    request: () => fetcher(),
  })
}

export function useAdminNullableDataQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: () => ReturnType<AdminDataFetcher<T>> | null,
  enabled: AdminEnabled
) {
  return useNullableApiQuery<T>({
    queryKey,
    request: () => fetcher(),
    enabled,
  })
}
