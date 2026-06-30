import { describe, expect, it, vi } from 'vitest'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'
import {
  mapApiDataResponse,
  mapApiPageResponse,
  unwrapApiData,
  unwrapApiPageData,
  unwrapAxiosApiData,
  unwrapAxiosApiPageData,
} from '../response'

const apiResponse = <T>(data: T, success = true): ApiResponse<T> => ({
  success,
  data,
})

const axiosApiResponse = <T>(data: T, success = true): AxiosResponse<ApiResponse<T>> => ({
  data: apiResponse(data, success),
  status: 200,
  statusText: 'OK',
  headers: {},
  config: {} as InternalAxiosRequestConfig,
})

describe('api response helpers', () => {
  it('unwraps raw and axios API data responses', () => {
    expect(unwrapApiData(apiResponse({ id: 1 }))).toEqual({ id: 1 })
    expect(unwrapAxiosApiData(axiosApiResponse(null))).toBeNull()
  })

  it('unwraps and normalizes page responses', () => {
    const rawPage = {
      content: ['a'],
      page: 2,
      size: 1,
      totalElements: 3,
      totalPages: 3,
      hasNext: false,
    }

    expect(unwrapApiPageData(apiResponse(rawPage))).toEqual({
      content: ['a'],
      totalElements: 3,
      totalPages: 3,
      size: 1,
      number: 2,
      first: false,
      last: true,
      empty: false,
    })
    expect(unwrapAxiosApiPageData(axiosApiResponse(rawPage)).number).toBe(2)
  })

  it('does not map nullish data unless requested', () => {
    const mapper = vi.fn((value: string | null) => value?.toUpperCase() ?? 'fallback')

    const skipped = mapApiDataResponse(axiosApiResponse(null), mapper)
    const mapped = mapApiDataResponse(axiosApiResponse(null), mapper, { mapNullish: true })

    expect(skipped.data.data).toBeNull()
    expect(mapped.data.data).toBe('fallback')
    expect(mapper).toHaveBeenCalledTimes(1)
  })

  it('preserves unsuccessful envelopes without mapping', () => {
    const response = axiosApiResponse('source', false)
    const mapper = vi.fn((value: string) => value.toUpperCase())

    const mapped = mapApiDataResponse(response, mapper)

    expect(mapped.data).toEqual(response.data)
    expect(mapper).not.toHaveBeenCalled()
  })

  it('maps page responses through the shared data mapper', () => {
    const mapped = mapApiPageResponse(
      axiosApiResponse(['a', 'b']),
      values => ({
        content: values.map(value => value.toUpperCase()),
        totalElements: values.length,
        totalPages: 1,
        size: values.length,
        number: 0,
        first: true,
        last: true,
        empty: false,
      })
    )

    expect(mapped.data.data.content).toEqual(['A', 'B'])
  })
})
