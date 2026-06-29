import type { AxiosResponse } from 'axios'
import type { ApiResponse, PageResponse } from '@/types'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'

export function unwrapApiData<T>(response: ApiResponse<T>): T {
    return response.data
}

export function unwrapAxiosApiData<T>(response: AxiosResponse<ApiResponse<T>>): T {
    return unwrapApiData(response.data)
}

export function unwrapApiPageData<T>(response: ApiResponse<PageResponse<T> | PageResponseRaw<T>>): PageResponse<T> {
    return normalizePageResponse(unwrapApiData(response))
}

export function unwrapAxiosApiPageData<T>(
    response: AxiosResponse<ApiResponse<PageResponse<T> | PageResponseRaw<T>>>
): PageResponse<T> {
    return unwrapApiPageData(response.data)
}

export function mapApiDataResponse<TSource, TTarget>(
    response: AxiosResponse<ApiResponse<TSource>>,
    mapper: (source: TSource) => TTarget,
    options: { mapNullish?: boolean } = {},
): AxiosResponse<ApiResponse<TTarget>> {
    if (!response.data.success || (!options.mapNullish && response.data.data == null)) {
        return response as unknown as AxiosResponse<ApiResponse<TTarget>>
    }

    return {
        ...response,
        data: {
            ...response.data,
            data: mapper(unwrapApiData(response.data)),
        },
    }
}

export function mapApiPageResponse<TSource, TTarget>(
    response: AxiosResponse<ApiResponse<TSource>>,
    mapper: (source: TSource) => PageResponse<TTarget>,
    options?: { mapNullish?: boolean },
): AxiosResponse<ApiResponse<PageResponse<TTarget>>> {
    return mapApiDataResponse(response, mapper, options)
}
