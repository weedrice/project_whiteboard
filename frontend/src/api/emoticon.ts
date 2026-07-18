import api from './index'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { mapApiDataResponse, unwrapApiData } from '@/api/response'
import type {
    EmoticonCreateRequest,
    EmoticonMaster,
    EmoticonPurchaseStatus,
    EmoticonSearchParams,
    EmoticonUpdateRequest,
} from '@/types/emoticon'
import type { ApiResponse } from '@/types'
import type { PageResponse } from '@/types/common'
import { normalizePageResponseItems, type PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

type EmoticonResponse<T> = AxiosResponse<ApiResponse<T>>
type EmoticonPeriod = 'daily' | 'weekly' | 'monthly'
type EmoticonPageParams = { page?: number; size?: number }
type EmoticonMasterWire = Omit<EmoticonMaster, 'images'> & {
    images?: EmoticonMaster['images'] | null
}

export function normalizeEmoticonMaster(emoticon: EmoticonMasterWire): EmoticonMaster {
    return {
        ...emoticon,
        images: emoticon.images ?? [],
    }
}

const mapEmoticonResponse = (response: EmoticonResponse<EmoticonMasterWire>) =>
    mapApiDataResponse(response, normalizeEmoticonMaster)

const mapEmoticonListResponse = (response: EmoticonResponse<EmoticonMasterWire[]>) =>
    mapApiDataResponse(response, (emoticons) => emoticons.map(normalizeEmoticonMaster))

const mapEmoticonPageResponse = (response: EmoticonResponse<PageResponseRaw<EmoticonMasterWire>>) =>
    mapApiDataResponse(
        response,
        (page): PageResponse<EmoticonMaster> => normalizePageResponseItems(page, normalizeEmoticonMaster),
    )

export function unwrapEmoticonResponse<T>(response: EmoticonResponse<T>): T {
    return unwrapApiData(response.data)
}

export const emoticonApi = {
    // List emoticon packs.
    getEmoticons(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons', { ...config, params })
            .then(mapEmoticonPageResponse)
    },
    async getEmoticonsData(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.getEmoticons(params, config))
    },

    // List popular emoticon packs by period.
    getPopularEmoticons(period: EmoticonPeriod = 'daily', config?: AxiosRequestConfig) {
        return api.get<ApiResponse<EmoticonMasterWire[]>>('/emoticons/popular', {
            ...config,
            params: { ...config?.params, period },
        }).then(mapEmoticonListResponse)
    },
    async getPopularEmoticonsData(period: EmoticonPeriod = 'daily', config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.getPopularEmoticons(period, config))
    },

    // Search by keyword, tag, creator, or pack name.
    searchAll(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons/search/all', { ...config, params })
            .then(mapEmoticonPageResponse)
    },
    async searchAllData(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.searchAll(params, config))
    },

    // Search emoticon packs by tag.
    searchByTag(tag: string, params?: EmoticonPageParams) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons/search/tag', {
            params: { tag, ...params },
        }).then(mapEmoticonPageResponse)
    },
    async searchByTagData(tag: string, params?: EmoticonPageParams) {
        return unwrapEmoticonResponse(await emoticonApi.searchByTag(tag, params))
    },

    // Search emoticon packs by keyword.
    searchByKeyword(keyword: string, params?: EmoticonPageParams) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons/search', {
            params: { keyword, ...params },
        }).then(mapEmoticonPageResponse)
    },
    async searchByKeywordData(keyword: string, params?: EmoticonPageParams) {
        return unwrapEmoticonResponse(await emoticonApi.searchByKeyword(keyword, params))
    },

    // List emoticon packs owned by the current user.
    getMyEmoticons(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons/my', { ...config, params })
            .then(mapEmoticonPageResponse)
    },
    async getMyEmoticonsData(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.getMyEmoticons(params, config))
    },

    // Get emoticon pack detail.
    getEmoticon(emoticonId: number, config?: AxiosRequestConfig) {
        const encodedId = encodePathSegment(emoticonId)
        if (config) {
            return api.get<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}`, config).then(mapEmoticonResponse)
        }
        return api.get<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}`).then(mapEmoticonResponse)
    },
    async getEmoticonData(emoticonId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.getEmoticon(emoticonId, config))
    },

    // Create an emoticon pack.
    createEmoticon(data: EmoticonCreateRequest, config?: AxiosRequestConfig) {
        if (config) {
            return api.post<ApiResponse<EmoticonMasterWire>>('/emoticons', data, config).then(mapEmoticonResponse)
        }
        return api.post<ApiResponse<EmoticonMasterWire>>('/emoticons', data).then(mapEmoticonResponse)
    },
    async createEmoticonData(data: EmoticonCreateRequest, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.createEmoticon(data, config))
    },

    // Update an emoticon pack.
    updateEmoticon(emoticonId: number, data: EmoticonUpdateRequest, config?: AxiosRequestConfig) {
        const encodedId = encodePathSegment(emoticonId)
        if (config) {
            return api.put<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}`, data, config).then(mapEmoticonResponse)
        }
        return api.put<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}`, data).then(mapEmoticonResponse)
    },
    async updateEmoticonData(emoticonId: number, data: EmoticonUpdateRequest, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.updateEmoticon(emoticonId, data, config))
    },

    // Toggle sale or visibility state.
    toggleVisibility(emoticonId: number, config?: AxiosRequestConfig) {
        const url = `/emoticons/${encodePathSegment(emoticonId)}/visibility`
        const request = config
            ? api.patch<ApiResponse<EmoticonMasterWire>>(url, undefined, config)
            : api.patch<ApiResponse<EmoticonMasterWire>>(url)
        return request.then(mapEmoticonResponse)
    },
    async toggleVisibilityData(emoticonId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.toggleVisibility(emoticonId, config))
    },

    // Delete an emoticon pack.
    deleteEmoticon(emoticonId: number) {
        return api.delete(`/emoticons/${encodePathSegment(emoticonId)}`)
    },

    // Add an image.
    addImage(emoticonId: number, fileId: number, config?: AxiosRequestConfig) {
        const encodedId = encodePathSegment(emoticonId)
        if (config) {
            return api.post<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}/images`, { fileId }, config)
                .then(mapEmoticonResponse)
        }
        return api.post<ApiResponse<EmoticonMasterWire>>(`/emoticons/${encodedId}/images`, { fileId })
            .then(mapEmoticonResponse)
    },
    async addImageData(emoticonId: number, fileId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.addImage(emoticonId, fileId, config))
    },

    // Delete an image.
    deleteImage(imageId: number, config?: AxiosRequestConfig) {
        const encodedId = encodePathSegment(imageId)
        if (config) {
            return api.delete(`/emoticons/images/${encodedId}`, config)
        }
        return api.delete(`/emoticons/images/${encodedId}`)
    },

    // Purchase an emoticon pack.
    purchaseEmoticon(emoticonId: number, config?: AxiosRequestConfig) {
        const url = `/emoticons/${encodePathSegment(emoticonId)}/purchase`
        const request = config
            ? api.post<ApiResponse<EmoticonMasterWire>>(url, undefined, config)
            : api.post<ApiResponse<EmoticonMasterWire>>(url)
        return request
            .then(mapEmoticonResponse)
    },
    async purchaseEmoticonData(emoticonId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.purchaseEmoticon(emoticonId, config))
    },

    // List purchased emoticon packs.
    getPurchasedEmoticons(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<EmoticonMasterWire>>>('/emoticons/purchased', { ...config, params })
            .then(mapEmoticonPageResponse)
    },
    async getPurchasedEmoticonsData(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.getPurchasedEmoticons(params, config))
    },

    // Check purchase status.
    checkPurchaseStatus(emoticonId: number, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<EmoticonPurchaseStatus>>(`/emoticons/${encodePathSegment(emoticonId)}/purchased`, config)
            : api.get<ApiResponse<EmoticonPurchaseStatus>>(`/emoticons/${encodePathSegment(emoticonId)}/purchased`)
    },
    async checkPurchaseStatusData(emoticonId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await emoticonApi.checkPurchaseStatus(emoticonId, config))
    },
}
