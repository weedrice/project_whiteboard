import type { QueryKey } from '@tanstack/vue-query'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
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
export type AdminApiRequestConfig = Pick<AxiosRequestConfig, 'signal'>

export type AdminPageFetcher<T> = (
  config?: AdminApiRequestConfig
) => Promise<AxiosResponse<ApiResponse<PageResponse<T> | PageResponseRaw<T>>>>
export type AdminDataFetcher<T> = (
  config?: AdminApiRequestConfig
) => Promise<AxiosResponse<ApiResponse<T>>>

function toAdminApiRequestConfig(signal?: AbortSignal): AdminApiRequestConfig | undefined {
  return signal ? { signal } : undefined
}

export function useAdminPageQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: AdminPageFetcher<T>,
  options: { enabled?: AdminEnabled } = {}
) {
  return useApiPageQuery<T>({
    queryKey,
    request: (context) => fetcher(toAdminApiRequestConfig(context?.signal)),
    enabled: options.enabled,
  })
}

export function useAdminNullablePageQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: (config?: AdminApiRequestConfig) => ReturnType<AdminPageFetcher<T>> | null,
  enabled: AdminEnabled
) {
  return useNullableApiPageQuery<T>({
    queryKey,
    request: (context) => fetcher(toAdminApiRequestConfig(context?.signal)),
    enabled,
  })
}

export function useAdminDataQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: AdminDataFetcher<T>
) {
  return useApiQuery<T>({
    queryKey,
    request: (context) => fetcher(toAdminApiRequestConfig(context?.signal)),
  })
}

export function useAdminNullableDataQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: (config?: AdminApiRequestConfig) => ReturnType<AdminDataFetcher<T>> | null,
  enabled: AdminEnabled
) {
  return useNullableApiQuery<T>({
    queryKey,
    request: (context) => fetcher(toAdminApiRequestConfig(context?.signal)),
    enabled,
  })
}
