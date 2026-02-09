import api from './index'
import type { EmoticonMaster, EmoticonCreateRequest, EmoticonUpdateRequest, EmoticonSearchParams, EmoticonPurchaseStatus } from '@/types/emoticon'
import type { PageResponse } from '@/types/common'

export const emoticonApi = {
    /**
     * 이모티콘 목록 조회
     */
    getEmoticons(params?: EmoticonSearchParams) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons', { params })
    },

    /**
     * 인기 이모티콘 조회 (일간/주간/월간)
     */
    getPopularEmoticons(period: 'daily' | 'weekly' | 'monthly' = 'daily') {
        return api.get<{ data: EmoticonMaster[] }>('/emoticons/popular', { params: { period } })
    },

    /**
     * 통합 검색 (태그, 등록자명, 이모티콘 이름)
     */
    searchAll(params?: EmoticonSearchParams) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search/all', { params })
    },

    /**
     * 태그로 이모티콘 검색
     */
    searchByTag(tag: string, params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search/tag', {
            params: { tag, ...params }
        })
    },

    /**
     * 키워드로 이모티콘 검색
     */
    searchByKeyword(keyword: string, params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/search', {
            params: { keyword, ...params }
        })
    },

    /**
     * 내 이모티콘 목록
     */
    getMyEmoticons(params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/my', { params })
    },

    /**
     * 이모티콘 상세 조회
     */
    getEmoticon(emoticonId: number) {
        return api.get<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}`)
    },

    /**
     * 이모티콘 생성
     */
    createEmoticon(data: EmoticonCreateRequest) {
        return api.post<{ data: EmoticonMaster }>('/emoticons', data)
    },

    /**
     * 이모티콘 수정
     */
    updateEmoticon(emoticonId: number, data: EmoticonUpdateRequest) {
        return api.put<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}`, data)
    },

    /**
     * 노비콘 숨김/표시 전환 (판매 중단 시 사용)
     */
    toggleVisibility(emoticonId: number) {
        return api.patch<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}/visibility`)
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
    addImage(emoticonId: number, imageUrl: string) {
        return api.post<{ data: EmoticonMaster }>(`/emoticons/${emoticonId}/images`, { imageUrl })
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

    /**
     * 구매한 이모티콘 목록
     */
    getPurchasedEmoticons(params?: { page?: number; size?: number }) {
        return api.get<{ data: PageResponse<EmoticonMaster> }>('/emoticons/purchased', { params })
    },

    /**
     * 이모티콘 구매 여부 확인
     */
    checkPurchaseStatus(emoticonId: number) {
        return api.get<{ data: EmoticonPurchaseStatus }>(`/emoticons/${emoticonId}/purchased`)
    }
}
