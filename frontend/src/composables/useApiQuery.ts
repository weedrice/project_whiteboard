import {
  keepPreviousData as keepPreviousQueryData,
  useQuery,
  type QueryFunctionContext,
  type QueryKey,
} from '@tanstack/vue-query'
import type { AxiosResponse } from 'axios'
import type { ComputedRef, Ref } from 'vue'
import type { ApiResponse, PageResponse } from '@/types'
import { unwrapAxiosApiData, unwrapAxiosApiPageData } from '@/api/response'
import type { PageResponseRaw } from '@/utils/pageResponse'

type QueryEnabled = boolean | Ref<boolean> | ComputedRef<boolean>
type ApiQueryKey = QueryKey | Ref<QueryKey> | ComputedRef<QueryKey>
type ApiRequest<TResponse> = (context: QueryFunctionContext) => Promise<AxiosResponse<ApiResponse<TResponse>>>
type ApiNullableRequest<TResponse> = (
  context: QueryFunctionContext
) => Promise<AxiosResponse<ApiResponse<TResponse>>> | null

interface ApiQueryOptions<TResponse, TData = TResponse> {
  queryKey: ApiQueryKey
  request: ApiRequest<TResponse>
  selectData?: (data: TResponse) => TData
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
  keepPreviousData?: boolean
}

interface ApiNullableQueryOptions<TResponse, TData = TResponse> {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<TResponse>
  selectData?: (data: TResponse) => TData
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
}

interface ApiPageQueryOptions<TItem, TData = PageResponse<TItem>> {
  queryKey: ApiQueryKey
  request: ApiRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  selectData?: (data: PageResponse<TItem>) => TData
  enabled?: QueryEnabled
  staleTime?: number
  keepPreviousData?: boolean
}

interface ApiNullablePageQueryOptions<TItem, TData = PageResponse<TItem>> {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  selectData?: (data: PageResponse<TItem>) => TData
  enabled?: QueryEnabled
  staleTime?: number
  keepPreviousData?: boolean
}

function previousDataPlaceholder(enabled?: boolean) {
  return enabled ? keepPreviousQueryData : undefined
}

export function useApiQuery<TResponse>(
  options: ApiQueryOptions<TResponse> & { selectData?: undefined } & Record<string, unknown>
): ReturnType<typeof useQuery<TResponse, Error, TResponse>>
export function useApiQuery<TResponse, TData>(
  options: ApiQueryOptions<TResponse, TData> & { selectData: (data: TResponse) => TData } & Record<string, unknown>
): ReturnType<typeof useQuery<TData, Error, TData>>
export function useApiQuery<TResponse, TData = TResponse>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  refetchInterval,
  keepPreviousData = false,
  ...queryOptions
}: ApiQueryOptions<TResponse, TData> & Record<string, unknown>) {
  return useQuery<TData, Error, TData>({
    ...(queryOptions as object),
    queryKey,
    queryFn: async (context) => {
      const data = unwrapAxiosApiData(await request(context))
      return selectData ? selectData(data) : (data as unknown as TData)
    },
    enabled,
    staleTime,
    refetchInterval,
    placeholderData: previousDataPlaceholder(keepPreviousData),
  })
}

export function useNullableApiQuery<TResponse>(
  options: ApiNullableQueryOptions<TResponse> & { selectData?: undefined } & Record<string, unknown>
): ReturnType<typeof useQuery<TResponse | null, Error, TResponse | null>>
export function useNullableApiQuery<TResponse, TData>(
  options: ApiNullableQueryOptions<TResponse, TData> & { selectData: (data: TResponse) => TData } & Record<string, unknown>
): ReturnType<typeof useQuery<TData | null, Error, TData | null>>
export function useNullableApiQuery<TResponse, TData = TResponse>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  refetchInterval,
  ...queryOptions
}: ApiNullableQueryOptions<TResponse, TData> & Record<string, unknown>) {
  return useQuery<TData | null, Error, TData | null>({
    ...(queryOptions as object),
    queryKey,
    queryFn: async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      const data = unwrapAxiosApiData(response)
      return selectData ? selectData(data) : (data as unknown as TData)
    },
    enabled,
    staleTime,
    refetchInterval,
  })
}

export function useApiPageQuery<TItem>(
  options: ApiPageQueryOptions<TItem> & { selectData?: undefined } & Record<string, unknown>
): ReturnType<typeof useQuery<PageResponse<TItem>, Error, PageResponse<TItem>>>
export function useApiPageQuery<TItem, TData>(
  options: ApiPageQueryOptions<TItem, TData> & { selectData: (data: PageResponse<TItem>) => TData } & Record<string, unknown>
): ReturnType<typeof useQuery<TData, Error, TData>>
export function useApiPageQuery<TItem, TData = PageResponse<TItem>>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  keepPreviousData = true,
  ...queryOptions
}: ApiPageQueryOptions<TItem, TData> & Record<string, unknown>) {
  return useQuery<TData, Error, TData>({
    ...(queryOptions as object),
    queryKey,
    queryFn: async (context) => {
      const page = unwrapAxiosApiPageData(await request(context))
      return selectData ? selectData(page) : (page as unknown as TData)
    },
    enabled,
    staleTime,
    placeholderData: previousDataPlaceholder(keepPreviousData),
  })
}

export function useNullableApiPageQuery<TItem>(
  options: ApiNullablePageQueryOptions<TItem> & { selectData?: undefined } & Record<string, unknown>
): ReturnType<typeof useQuery<PageResponse<TItem> | null, Error, PageResponse<TItem> | null>>
export function useNullableApiPageQuery<TItem, TData>(
  options: ApiNullablePageQueryOptions<TItem, TData> & { selectData: (data: PageResponse<TItem>) => TData } & Record<string, unknown>
): ReturnType<typeof useQuery<TData | null, Error, TData | null>>
export function useNullableApiPageQuery<TItem, TData = PageResponse<TItem>>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  keepPreviousData = true,
  ...queryOptions
}: ApiNullablePageQueryOptions<TItem, TData> & Record<string, unknown>) {
  return useQuery<TData | null, Error, TData | null>({
    ...(queryOptions as object),
    queryKey,
    queryFn: async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      const page = unwrapAxiosApiPageData(response)
      return selectData ? selectData(page) : (page as unknown as TData)
    },
    enabled,
    staleTime,
    placeholderData: previousDataPlaceholder(keepPreviousData),
  })
}
