import api from './index'
import type { EmoticonMaster, EmoticonCreateRequest, EmoticonUpdateRequest, EmoticonSearchParams, EmoticonPurchaseStatus } from '@/types/emoticon'
import type { ApiResponse } from '@/types'
import type { PageResponse } from '@/types/common'

type EmoticonEnvelopeResponse<T> = {
    data: {
        data: T
    }
}

export function unwrapEmoticonResponse<T>(response: EmoticonEnvelopeResponse<T>): T {
    return response.data.data
}

export const emoticonApi = {
    /**
     * 이모티콘 목록 조회
     */
    getEmoticons(params?: EmoticonSearchParams) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons', { params })
    },
    async getEmoticonsData(params?: EmoticonSearchParams) {
        return unwrapEmoticonResponse(await this.getEmoticons(params))
    },

    /**
     * 인기 이모티콘 조회 (일간/주간/월간)
     */
    getPopularEmoticons(period: 'daily' | 'weekly' | 'monthly' = 'daily') {
        return api.get<{ data: EmoticonMaster[] }>('/emoticons/popular', { params: { period } })
    },
    async getPopularEmoticonsData(period: 'daily' | 'weekly' | 'monthly' = 'daily') {
        return unwrapEmoticonResponse(await this.getPopularEmoticons(period))
    },

    /**
     * 통합 검색 (태그, 등록자명, 이모티콘 이름)
     */
    searchAll(params?: EmoticonSearchParams) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search/all', { params })
    },
    async searchAllData(params?: EmoticonSearchParams) {
        return unwrapEmoticonResponse(await this.searchAll(params))
    },

    /**
     * 태그로 이모티콘 검색
     */
    searchByTag(tag: string, params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search/tag', {
            params: { tag, ...params }
        })
    },
    async searchByTagData(tag: string, params?: { page?: number; size?: number }) {
        return unwrapEmoticonResponse(await this.searchByTag(tag, params))
    },

    /**
     * 키워드로 이모티콘 검색
     */
    searchByKeyword(keyword: string, params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search', {
            params: { keyword, ...params }
        })
    },
    async searchByKeywordData(keyword: string, params?: { page?: number; size?: number }) {
        return unwrapEmoticonResponse(await this.searchByKeyword(keyword, params))
    },

    /**
     * 내 이모티콘 목록
     */
    getMyEmoticons(params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/my', { params })
    },
    async getMyEmoticonsData(params?: { page?: number; size?: number }) {
        return unwrapEmoticonResponse(await this.getMyEmoticons(params))
    },

    /**
     * 이모티콘 상세 조회
     */
    getEmoticon(emoticonId: number) {
        return api.get<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}`)
    },
    async getEmoticonData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.getEmoticon(emoticonId))
    },

    /**
     * 이모티콘 생성
     */
    createEmoticon(data: EmoticonCreateRequest) {
        return api.post<{ data: EmoticonMaster }>('/emoticons', data)
    },
    async createEmoticonData(data: EmoticonCreateRequest) {
        return unwrapEmoticonResponse(await this.createEmoticon(data))
    },

    /**
     * 이모티콘 수정
     */
    updateEmoticon(emoticonId: number, data: EmoticonUpdateRequest) {
        return api.put<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}`, data)
    },
    async updateEmoticonData(emoticonId: number, data: EmoticonUpdateRequest) {
        return unwrapEmoticonResponse(await this.updateEmoticon(emoticonId, data))
    },

    /**
     * 노비콘 숨김/표시 전환 (판매 중단 시 사용)
     */
    toggleVisibility(emoticonId: number) {
        return api.patch<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}/visibility`)
    },
    async toggleVisibilityData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.toggleVisibility(emoticonId))
    },

    /**
     * 이모티콘 삭제
     */
    deleteEmoticon(emoticonId: number) {
        return api.delete(`/emoticons/${emoticonId}`)
    },

    /**
     * 이미지 추가
     */
    addImage(emoticonId: number, fileId: number) {
        return api.post<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}/images`, { fileId })
    },
    async addImageData(emoticonId: number, fileId: number) {
        return unwrapEmoticonResponse(await this.addImage(emoticonId, fileId))
    },

    /**
     * 이미지 삭제
     */
    deleteImage(imageId: number) {
        return api.delete(`/emoticons/images/${imageId}`)
    },

    /**
     * 이모티콘 구매
     */
    purchaseEmoticon(emoticonId: number) {
        return api.post<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}/purchase`)
    },
    async purchaseEmoticonData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.purchaseEmoticon(emoticonId))
    },

    /**
     * 구매한 이모티콘 목록
     */
    getPurchasedEmoticons(params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/purchased', { params })
    },
    async getPurchasedEmoticonsData(params?: { page?: number; size?: number }) {
        return unwrapEmoticonResponse(await this.getPurchasedEmoticons(params))
    },

    /**
     * 이모티콘 구매 여부 확인
     */
    checkPurchaseStatus(emoticonId: number) {
        return api.get<ApiResponse<EmoticonPurchaseStatus>>(`/emoticons/${emoticonId}/purchased`)
    },
    async checkPurchaseStatusData(emoticonId: number) {
        return unwrapEmoticonResponse(await this.checkPurchaseStatus(emoticonId))
    }
}
