import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'
import {
  useApiPageQuery,
  useApiQuery,
  useNullableApiPageQuery,
  useNullableApiQuery,
} from '../useApiQuery'

const vueQueryMock = vi.hoisted(() => ({
  useQuery: vi.fn((options: Record<string, unknown>) => ({
    options,
  })),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vueQueryMock.useQuery,
}))

type CapturedQueryOptions = {
  queryFn: (context: QueryFunctionContext) => Promise<unknown>
  placeholderData?: unknown
  enabled?: unknown
  staleTime?: unknown
  refetchInterval?: unknown
  meta?: unknown
  retry?: unknown
  retryDelay?: unknown
  gcTime?: unknown
  refetchOnMount?: unknown
  refetchOnWindowFocus?: unknown
  refetchOnReconnect?: unknown
  networkMode?: unknown
}

const axiosApiResponse = <T>(data: T): AxiosResponse<ApiResponse<T>> => ({
  data: {
    success: true,
    data,
  },
  status: 200,
  statusText: 'OK',
  headers: {},
  config: {} as InternalAxiosRequestConfig,
})

const latestQueryOptions = () =>
  vi.mocked(useQuery).mock.calls.at(-1)?.[0] as unknown as CapturedQueryOptions

const queryContext = { queryKey: ['test'] } as unknown as QueryFunctionContext

describe('useApiQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('selects unwrapped data and passes through query options', async () => {
    const request = vi.fn(async () => axiosApiResponse({ id: 1, name: 'first' }))

    useApiQuery({
      queryKey: ['item', 1],
      request,
      selectData: data => data.name,
      enabled: true,
      staleTime: 1000,
      refetchInterval: 5000,
      keepPreviousData: true,
      meta: { feature: 'common-query' },
      retry: 2,
      retryDelay: 100,
      gcTime: 60_000,
      refetchOnMount: 'always',
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
      networkMode: 'always',
    })

    const options = latestQueryOptions()

    await expect(options.queryFn(queryContext)).resolves.toBe('first')
    expect(request).toHaveBeenCalledWith(queryContext)
    expect(options).toEqual(expect.objectContaining({
      enabled: true,
      staleTime: 1000,
      refetchInterval: 5000,
      meta: { feature: 'common-query' },
      retry: 2,
      retryDelay: 100,
      gcTime: 60_000,
      refetchOnMount: 'always',
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
      networkMode: 'always',
    }))
    expect(typeof options.placeholderData).toBe('function')
    expect((options.placeholderData as (previousData: unknown) => unknown)('previous')).toBe('previous')
  })

  it('returns null from nullable queries without running selectData', async () => {
    const request = vi.fn(async () => null)
    const selectData = vi.fn((data: { value: string }) => data.value)

    useNullableApiQuery({
      queryKey: ['nullable'],
      request,
      selectData,
      meta: { nullable: true },
    })

    const options = latestQueryOptions()

    await expect(options.queryFn(queryContext)).resolves.toBeNull()
    expect(selectData).not.toHaveBeenCalled()
    expect(options.meta).toEqual({ nullable: true })
  })

  it('selects normalized page data and keeps previous data by default', async () => {
    const request = vi.fn(async () => axiosApiResponse({
      content: [{ id: 1 }, { id: 2 }],
      page: 2,
      size: 2,
      totalElements: 5,
      totalPages: 3,
      hasNext: false,
    }))

    useApiPageQuery({
      queryKey: ['page'],
      request,
      selectData: page => ({
        ids: page.content.map(item => item.id),
        number: page.number,
        last: page.last,
      }),
    })

    const options = latestQueryOptions()

    await expect(options.queryFn(queryContext)).resolves.toEqual({
      ids: [1, 2],
      number: 2,
      last: true,
    })
    expect(typeof options.placeholderData).toBe('function')
    expect((options.placeholderData as (previousData: unknown) => unknown)('previous-page')).toBe('previous-page')
  })

  it('can disable previous page placeholder data', () => {
    useApiPageQuery({
      queryKey: ['page', 2],
      request: async () => axiosApiResponse({ content: [] }),
      keepPreviousData: false,
    })

    expect(latestQueryOptions().placeholderData).toBeUndefined()
  })

  it('returns null from nullable page queries before normalizing data', async () => {
    const request = vi.fn(async () => null)
    const selectData = vi.fn(page => page.content)

    useNullableApiPageQuery({
      queryKey: ['nullable-page'],
      request,
      selectData,
    })

    await expect(latestQueryOptions().queryFn(queryContext)).resolves.toBeNull()
    expect(selectData).not.toHaveBeenCalled()
  })
})
