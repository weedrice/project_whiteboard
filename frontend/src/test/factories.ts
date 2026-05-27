import type { AxiosResponse } from 'axios'
import type { ApiResponse, PageResponse } from '@/types'

export function apiSuccess<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
  }
}

export function apiEmptySuccess(): ApiResponse<void> {
  return {
    success: true,
    data: undefined,
  }
}

export function axiosApiResponse<T>(data: ApiResponse<T>): AxiosResponse<ApiResponse<T>> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {
      headers: undefined,
    },
  } as unknown as AxiosResponse<ApiResponse<T>>
}

export function axiosApiSuccess<T>(data: T): AxiosResponse<ApiResponse<T>> {
  return axiosApiResponse(apiSuccess(data))
}

export function pageResponse<T>(
  content: T[],
  overrides: Partial<PageResponse<T>> = {},
): PageResponse<T> {
  const number = overrides.number ?? 0
  const size = overrides.size ?? content.length
  const totalElements = overrides.totalElements ?? content.length
  const totalPages = overrides.totalPages ?? 1

  return {
    content,
    number,
    size,
    totalElements,
    totalPages,
    first: overrides.first ?? (number === 0),
    last: overrides.last ?? (number >= totalPages - 1),
    empty: overrides.empty ?? (content.length === 0),
    ...overrides,
  }
}
