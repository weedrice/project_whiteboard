import api from './index'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { unwrapApiData } from '@/api/response'
import type {
    EmoticonCreateRequest,
    EmoticonMaster,
    EmoticonPurchaseStatus,
    EmoticonSearchParams,
    EmoticonUpdateRequest,
} from '@/types/emoticon'
import type { ApiResponse } from '@/types'
import type { PageResponse } from '@/types/common'

type EmoticonResponse<T> = AxiosResponse<ApiResponse<T>>
type EmoticonPeriod = 'daily' | 'weekly' | 'monthly'
type EmoticonPageParams = { page?: number; size?: number }

export function unwrapEmoticonResponse<T>(response: EmoticonResponse<T>): T {
    return unwrapApiData(response.data)
}

export const emoticonApi = {
    // List emoticon packs.
    getEmoticons(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons', { ...config, params })
    },
    async getEmoticonsData(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.getEmoticons(params, config))
    },

    // List popular emoticon packs by period.
    getPopularEmoticons(period: EmoticonPeriod = 'daily', config?: AxiosRequestConfig) {
        return api.get<ApiResponse<EmoticonMaster[]>>('/emoticons/popular', {
            ...config,
            params: { ...config?.params, period },
        })
    },
    async getPopularEmoticonsData(period: EmoticonPeriod = 'daily', config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.getPopularEmoticons(period, config))
    },

    // Search by keyword, tag, creator, or pack name.
    searchAll(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons/search/all', { ...config, params })
    },
    async searchAllData(params?: EmoticonSearchParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.searchAll(params, config))
    },

    // Search emoticon packs by tag.
    searchByTag(tag: string, params?: EmoticonPageParams) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons/search/tag', {
            params: { tag, ...params },
        })
    },
    async searchByTagData(tag: string, params?: EmoticonPageParams) {
        return unwrapEmoticonResponse(await this.searchByTag(tag, params))
    },

    // Search emoticon packs by keyword.
    searchByKeyword(keyword: string, params?: EmoticonPageParams) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons/search', {
            params: { keyword, ...params },
        })
    },
    async searchByKeywordData(keyword: string, params?: EmoticonPageParams) {
        return unwrapEmoticonResponse(await this.searchByKeyword(keyword, params))
    },

    // List emoticon packs owned by the current user.
    getMyEmoticons(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons/my', { ...config, params })
    },
    async getMyEmoticonsData(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.getMyEmoticons(params, config))
    },

    // Get emoticon pack detail.
    getEmoticon(emoticonId: number, config?: AxiosRequestConfig) {
        if (config) {
            return api.get<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}`, config)
        }
        return api.get<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}`)
    },
    async getEmoticonData(emoticonId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.getEmoticon(emoticonId, config))
    },

    // Create an emoticon pack.
    createEmoticon(data: EmoticonCreateRequest, config?: AxiosRequestConfig) {
        if (config) {
            return api.post<ApiResponse<EmoticonMaster>>('/emoticons', data, config)
        }
        return api.post<ApiResponse<EmoticonMaster>>('/emoticons', data)
    },
    async createEmoticonData(data: EmoticonCreateRequest, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.createEmoticon(data, config))
    },

    // Update an emoticon pack.
    updateEmoticon(emoticonId: number, data: EmoticonUpdateRequest, config?: AxiosRequestConfig) {
        if (config) {
            return api.put<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}`, data, config)
        }
        return api.put<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}`, data)
    },
    async updateEmoticonData(emoticonId: number, data: EmoticonUpdateRequest, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.updateEmoticon(emoticonId, data, config))
    },

    // Toggle sale or visibility state.
    toggleVisibility(emoticonId: number) {
        return api.patch<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}/visibility`)
    },
    async toggleVisibilityData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.toggleVisibility(emoticonId))
    },

    // Delete an emoticon pack.
    deleteEmoticon(emoticonId: number) {
        return api.delete(`/emoticons/${emoticonId}`)
    },

    // Add an image.
    addImage(emoticonId: number, fileId: number, config?: AxiosRequestConfig) {
        if (config) {
            return api.post<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}/images`, { fileId }, config)
        }
        return api.post<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}/images`, { fileId })
    },
    async addImageData(emoticonId: number, fileId: number, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.addImage(emoticonId, fileId, config))
    },

    // Delete an image.
    deleteImage(imageId: number, config?: AxiosRequestConfig) {
        if (config) {
            return api.delete(`/emoticons/images/${imageId}`, config)
        }
        return api.delete(`/emoticons/images/${imageId}`)
    },

    // Purchase an emoticon pack.
    purchaseEmoticon(emoticonId: number) {
        return api.post<ApiResponse<EmoticonMaster>>(`/emoticons/${emoticonId}/purchase`)
    },
    async purchaseEmoticonData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.purchaseEmoticon(emoticonId))
    },

    // List purchased emoticon packs.
    getPurchasedEmoticons(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<EmoticonMaster>>>('/emoticons/purchased', { ...config, params })
    },
    async getPurchasedEmoticonsData(params?: EmoticonPageParams, config?: AxiosRequestConfig) {
        return unwrapEmoticonResponse(await this.getPurchasedEmoticons(params, config))
    },

    // Check purchase status.
    checkPurchaseStatus(emoticonId: number) {
        return api.get<ApiResponse<EmoticonPurchaseStatus>>(`/emoticons/${emoticonId}/purchased`)
    },
    async checkPurchaseStatusData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.checkPurchaseStatus(emoticonId))
    },
}
