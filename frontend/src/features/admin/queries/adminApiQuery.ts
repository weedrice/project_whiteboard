import type { QueryFunctionContext, QueryKey } from '@tanstack/vue-query'
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

interface AdminPageQueryOptions<T, TData = PageResponse<T>> {
  enabled?: AdminEnabled
  selectData?: (data: PageResponse<T>) => TData
}

interface AdminDataQueryOptions<T, TData = T> {
  enabled?: AdminEnabled
  selectData?: (data: T) => TData
}

function toAdminApiRequestConfig(signal?: AbortSignal): AdminApiRequestConfig | undefined {
  return signal ? { signal } : undefined
}

const toConfigFromQueryContext = (context: QueryFunctionContext) =>
  toAdminApiRequestConfig(context?.signal)

export const callAdminApiWithOptionalConfig = <T>(
  config: AdminApiRequestConfig | undefined,
  requestWithConfig: (config: AdminApiRequestConfig) => T,
  requestWithoutConfig: () => T,
) => (config ? requestWithConfig(config) : requestWithoutConfig())

export function useAdminPageQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: AdminPageFetcher<T>,
  options?: AdminPageQueryOptions<T>
) : ReturnType<typeof useApiPageQuery<T>>
export function useAdminPageQuery<T, TData>(
  queryKey: AdminQueryKey,
  fetcher: AdminPageFetcher<T>,
  options: AdminPageQueryOptions<T, TData> & { selectData: (data: PageResponse<T>) => TData }
) : ReturnType<typeof useApiPageQuery<T, TData>>
export function useAdminPageQuery<T, TData = PageResponse<T>>(
  queryKey: AdminQueryKey,
  fetcher: AdminPageFetcher<T>,
  options: AdminPageQueryOptions<T, TData> = {}
) {
  const baseOptions = {
    queryKey,
    request: (context: QueryFunctionContext) => fetcher(toConfigFromQueryContext(context)),
    enabled: options.enabled,
  }
  return options.selectData
    ? useApiPageQuery<T, TData>({
        ...baseOptions,
        selectData: options.selectData,
      })
    : useApiPageQuery<T>(baseOptions)
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
  fetcher: AdminDataFetcher<T>,
  options?: AdminDataQueryOptions<T>
): ReturnType<typeof useApiQuery<T>>
export function useAdminDataQuery<T, TData>(
  queryKey: AdminQueryKey,
  fetcher: AdminDataFetcher<T>,
  options: AdminDataQueryOptions<T, TData> & { selectData: (data: T) => TData }
): ReturnType<typeof useApiQuery<T, TData>>
export function useAdminDataQuery<T, TData = T>(
  queryKey: AdminQueryKey,
  fetcher: AdminDataFetcher<T>,
  options: AdminDataQueryOptions<T, TData> = {}
) {
  const baseOptions = {
    queryKey,
    request: (context: QueryFunctionContext) => fetcher(toConfigFromQueryContext(context)),
    enabled: options.enabled,
  }
  return options.selectData
    ? useApiQuery<T, TData>({
        ...baseOptions,
        selectData: options.selectData,
      })
    : useApiQuery<T>(baseOptions)
}

export function useAdminNullableDataQuery<T>(
  queryKey: AdminQueryKey,
  fetcher: (config?: AdminApiRequestConfig) => ReturnType<AdminDataFetcher<T>> | null,
  enabled: AdminEnabled
): ReturnType<typeof useNullableApiQuery<T>>
export function useAdminNullableDataQuery<T, TData>(
  queryKey: AdminQueryKey,
  fetcher: (config?: AdminApiRequestConfig) => ReturnType<AdminDataFetcher<T>> | null,
  enabled: AdminEnabled,
  options: Pick<AdminDataQueryOptions<T, TData>, 'selectData'> & { selectData: (data: T) => TData }
): ReturnType<typeof useNullableApiQuery<T, TData>>
export function useAdminNullableDataQuery<T, TData = T>(
  queryKey: AdminQueryKey,
  fetcher: (config?: AdminApiRequestConfig) => ReturnType<AdminDataFetcher<T>> | null,
  enabled: AdminEnabled,
  options: Pick<AdminDataQueryOptions<T, TData>, 'selectData'> = {}
) {
  const baseOptions = {
    queryKey,
    request: (context: QueryFunctionContext) => fetcher(toConfigFromQueryContext(context)),
    enabled,
  }
  return options.selectData
    ? useNullableApiQuery<T, TData>({
        ...baseOptions,
        selectData: options.selectData,
      })
    : useNullableApiQuery<T>(baseOptions)
}
