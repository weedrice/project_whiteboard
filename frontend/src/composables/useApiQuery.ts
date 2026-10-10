import {
  useQuery,
  type QueryFunctionContext,
  type QueryKey,
} from '@tanstack/vue-query'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { type ComputedRef, type Ref } from 'vue'
import type { ApiResponse, PageResponse } from '@/types'
import { unwrapAxiosApiData, unwrapAxiosApiPageData } from '@/api/response'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { createReactiveAuthScopedQueryOptions, getAuthQuerySource } from '@/utils/authScopedQuery'

type QueryEnabled = boolean | Ref<boolean> | ComputedRef<boolean>
type ApiQueryKey = QueryKey | Ref<QueryKey> | ComputedRef<QueryKey>
export type ApiQueryContext = QueryFunctionContext & { requestConfig: AxiosRequestConfig }

type ApiRequest<TResponse> = (context: ApiQueryContext) => Promise<AxiosResponse<ApiResponse<TResponse>>>
type ApiNullableRequest<TResponse> = (
  context: ApiQueryContext
) => Promise<AxiosResponse<ApiResponse<TResponse>> | null> | null
type RefetchTriggerOption = boolean | 'always'
type RetryOption = boolean | number | ((failureCount: number, error: Error) => boolean)
type RetryDelayOption = number | ((attemptIndex: number, error: Error) => number)

interface ApiQueryPassthroughOptions {
  meta?: Record<string, unknown>
  retry?: RetryOption
  retryDelay?: RetryDelayOption
  gcTime?: number
  refetchOnMount?: RefetchTriggerOption
  refetchOnWindowFocus?: RefetchTriggerOption
  refetchOnReconnect?: RefetchTriggerOption
  networkMode?: 'online' | 'always' | 'offlineFirst'
}

interface ApiQueryOptions<TResponse, TData = TResponse> extends ApiQueryPassthroughOptions {
  queryKey: ApiQueryKey
  request: ApiRequest<TResponse>
  selectData?: (data: TResponse) => TData
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
  keepPreviousData?: boolean
}

interface ApiNullableQueryOptions<TResponse, TData = TResponse> extends ApiQueryPassthroughOptions {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<TResponse>
  selectData?: (data: TResponse) => TData
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
}

interface ApiPageQueryOptions<TItem, TData = PageResponse<TItem>> extends ApiQueryPassthroughOptions {
  queryKey: ApiQueryKey
  request: ApiRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  selectData?: (data: PageResponse<TItem>) => TData
  enabled?: QueryEnabled
  staleTime?: number
  refetchInterval?: number
  keepPreviousData?: boolean
}

interface ApiNullablePageQueryOptions<TItem, TData = PageResponse<TItem>> extends ApiQueryPassthroughOptions {
  queryKey: ApiQueryKey
  request: ApiNullableRequest<PageResponse<TItem> | PageResponseRaw<TItem>>
  selectData?: (data: PageResponse<TItem>) => TData
  enabled?: QueryEnabled
  staleTime?: number
  keepPreviousData?: boolean
}

function previousDataPlaceholder<TData>(enabled?: boolean) {
  return enabled ? ((previousData: TData | undefined) => previousData) : undefined
}

function resolveSelectedData<TResponse, TData>(
  data: TResponse,
  selectData?: (data: TResponse) => TData,
): TResponse | TData {
  return selectData ? selectData(data) : data
}

function resolveQueryOptions<T>(
  queryKey: ApiQueryKey,
  meta: Record<string, unknown> | undefined,
  request: (context: ApiQueryContext) => Promise<T>,
) {
  if (meta?.authScoped !== true) {
    return {
      queryKey,
      queryFn: (context: QueryFunctionContext) => request({
        ...context,
        requestConfig: { signal: context.signal },
      }),
    }
  }

  const source = getAuthQuerySource()
  const scopedOptions = createReactiveAuthScopedQueryOptions<T, never>(source, queryKey, request)
  return {
    queryKey: scopedOptions.queryKey,
    queryFn: scopedOptions.queryFn,
  }
}

export function useApiQuery<TResponse>(
  options: ApiQueryOptions<TResponse> & { selectData?: undefined }
): ReturnType<typeof useQuery<TResponse, Error, TResponse>>
export function useApiQuery<TResponse, TData>(
  options: ApiQueryOptions<TResponse, TData> & { selectData: (data: TResponse) => TData }
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
}: ApiQueryOptions<TResponse, TData>) {
  return useQuery<TResponse | TData, Error, TResponse | TData>({
    ...queryOptions,
    ...resolveQueryOptions(queryKey, queryOptions.meta, async (context) => {
      const data = unwrapAxiosApiData(await request(context))
      return resolveSelectedData(data, selectData)
    }),
    enabled,
    staleTime,
    refetchInterval,
    placeholderData: previousDataPlaceholder(
      keepPreviousData && queryOptions.meta?.authScoped !== true,
    ),
  })
}

export function useNullableApiQuery<TResponse>(
  options: ApiNullableQueryOptions<TResponse> & { selectData?: undefined }
): ReturnType<typeof useQuery<TResponse | null, Error, TResponse | null>>
export function useNullableApiQuery<TResponse, TData>(
  options: ApiNullableQueryOptions<TResponse, TData> & { selectData: (data: TResponse) => TData }
): ReturnType<typeof useQuery<TData | null, Error, TData | null>>
export function useNullableApiQuery<TResponse, TData = TResponse>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  refetchInterval,
  ...queryOptions
}: ApiNullableQueryOptions<TResponse, TData>) {
  return useQuery<TResponse | TData | null, Error, TResponse | TData | null>({
    ...queryOptions,
    ...resolveQueryOptions(queryKey, queryOptions.meta, async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      const data = unwrapAxiosApiData(response)
      return resolveSelectedData(data, selectData)
    }),
    enabled,
    staleTime,
    refetchInterval,
  })
}

export function useApiPageQuery<TItem>(
  options: ApiPageQueryOptions<TItem> & { selectData?: undefined }
): ReturnType<typeof useQuery<PageResponse<TItem>, Error, PageResponse<TItem>>>
export function useApiPageQuery<TItem, TData>(
  options: ApiPageQueryOptions<TItem, TData> & { selectData: (data: PageResponse<TItem>) => TData }
): ReturnType<typeof useQuery<TData, Error, TData>>
export function useApiPageQuery<TItem, TData = PageResponse<TItem>>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  refetchInterval,
  keepPreviousData = true,
  ...queryOptions
}: ApiPageQueryOptions<TItem, TData>) {
  return useQuery<PageResponse<TItem> | TData, Error, PageResponse<TItem> | TData>({
    ...queryOptions,
    ...resolveQueryOptions(queryKey, queryOptions.meta, async (context) => {
      const page = unwrapAxiosApiPageData(await request(context))
      return resolveSelectedData(page, selectData)
    }),
    enabled,
    staleTime,
    refetchInterval,
    placeholderData: previousDataPlaceholder(
      keepPreviousData && queryOptions.meta?.authScoped !== true,
    ),
  })
}

export function useNullableApiPageQuery<TItem>(
  options: ApiNullablePageQueryOptions<TItem> & { selectData?: undefined }
): ReturnType<typeof useQuery<PageResponse<TItem> | null, Error, PageResponse<TItem> | null>>
export function useNullableApiPageQuery<TItem, TData>(
  options: ApiNullablePageQueryOptions<TItem, TData> & { selectData: (data: PageResponse<TItem>) => TData }
): ReturnType<typeof useQuery<TData | null, Error, TData | null>>
export function useNullableApiPageQuery<TItem, TData = PageResponse<TItem>>({
  queryKey,
  request,
  selectData,
  enabled,
  staleTime,
  keepPreviousData = true,
  ...queryOptions
}: ApiNullablePageQueryOptions<TItem, TData>) {
  return useQuery<PageResponse<TItem> | TData | null, Error, PageResponse<TItem> | TData | null>({
    ...queryOptions,
    ...resolveQueryOptions(queryKey, queryOptions.meta, async (context) => {
      const response = await request(context)
      if (response === null) {
        return null
      }

      const page = unwrapAxiosApiPageData(response)
      return resolveSelectedData(page, selectData)
    }),
    enabled,
    staleTime,
    placeholderData: previousDataPlaceholder(
      keepPreviousData && queryOptions.meta?.authScoped !== true,
    ),
  })
}
