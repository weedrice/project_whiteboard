import { useQuery, type QueryFunctionContext, type QueryKey } from '@tanstack/vue-query'
import type { AxiosResponse } from 'axios'
import type { ComputedRef, Ref } from 'vue'
import type { ApiResponse, PageResponse } from '@/types'
import { unwrapAxiosApiData, unwrapAxiosApiPageData } from '@/api/response'
import type { PageResponseRaw } from '@/utils/pageResponse'

type QueryEnabled = boolean | Ref<boolean> | ComputedRef<boolean>
type ApiQueryKey = QueryKey | Ref<QueryKey> | ComputedRef<QueryKey>
type ApiRequest<TData> = (context: QueryFunctionContext) => Promise<AxiosResponse<ApiResponse<TData>>>
type ApiNullableRequest<TData> = (
  context: QueryFunctionContext
) => Promise<AxiosResponse<ApiResponse<TData>> | null>

interface ApiQueryOptions<TData> {
  queryKey: ApiQueryKey
  request: ApiRequest<TData>
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
  keepPreviousData?: boolean
}

interface ApiNullableQueryOptions<TData> {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<TData>
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
}

interface ApiPageQueryOptions<TItem> {
  queryKey: ApiQueryKey
  request: ApiRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  enabled?: QueryEnabled
  staleTime?: number
  keepPreviousData?: boolean
}

interface ApiNullablePageQueryOptions<TItem> {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  enabled?: QueryEnabled
  staleTime?: number
  keepPreviousData?: boolean
}

function previousDataPlaceholder<TData>(enabled?: boolean) {
  return enabled ? (previousData: TData | undefined) => previousData : undefined
}

export function useApiQuery<TData>({
  queryKey,
  request,
  enabled,
  staleTime,
  refetchInterval,
}: ApiQueryOptions<TData>) {
  return useQuery<TData, Error, TData>({
    queryKey,
    queryFn: async (context) => unwrapAxiosApiData(await request(context)),
    enabled,
    staleTime,
    refetchInterval,
  })
}

export function useNullableApiQuery<TData>({
  queryKey,
  request,
  enabled,
  staleTime,
  refetchInterval,
}: ApiNullableQueryOptions<TData>) {
  return useQuery<TData | null, Error, TData | null>({
    queryKey,
    queryFn: async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      return unwrapAxiosApiData(response)
    },
    enabled,
    staleTime,
    refetchInterval,
  })
}

export function useApiPageQuery<TItem>({
  queryKey,
  request,
  enabled,
  staleTime,
  keepPreviousData = true,
}: ApiPageQueryOptions<TItem>) {
  return useQuery<PageResponse<TItem>, Error, PageResponse<TItem>>({
    queryKey,
    queryFn: async (context) => unwrapAxiosApiPageData(await request(context)),
    enabled,
    staleTime,
    placeholderData: previousDataPlaceholder<PageResponse<TItem>>(keepPreviousData),
  })
}

export function useNullableApiPageQuery<TItem>({
  queryKey,
  request,
  enabled,
  staleTime,
  keepPreviousData = true,
}: ApiNullablePageQueryOptions<TItem>) {
  return useQuery<PageResponse<TItem> | null, Error, PageResponse<TItem> | null>({
    queryKey,
    queryFn: async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      return unwrapAxiosApiPageData(response)
    },
    enabled,
    staleTime,
    placeholderData: previousDataPlaceholder<PageResponse<TItem> | null>(keepPreviousData),
  })
}
