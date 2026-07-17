import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    ActionMessageResponse,
    DraftPostListResponse,
    MyComment,
    PointHistory,
    PointHistoryResponse,
    PostSeries,
    PostSeriesPayload,
    ScrapFolder,
    ScrapFolderPayload,
    ScrapListResponse,
    SubscriptionBoardListItem,
    UserPoint,
} from '@/types'
import { mapApiDataResponse, mapApiPageResponse } from '@/api/response'
import { normalizePostSummaryPage, type PostSummaryWire } from '@/api/postContract'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'
import {
    toBlockedUserPage,
    toScrapPostSummaryPage,
    type BlockListRawResponse,
} from '@/api/userMappers'

export interface PaginationParams {
    page?: number
    size?: number
    sort?: string
    [key: string]: unknown
}

interface SubscriptionParams extends PaginationParams {
    includeUnavailable?: boolean
}

export const userActivityApi = {
    blockUser(userId: string | number) {
        return api.post<ApiResponse<ActionMessageResponse>>(`/users/${encodePathSegment(userId)}/block`)
    },
    unblockUser(userId: string | number) {
        return api.delete<ApiResponse<ActionMessageResponse>>(`/users/${encodePathSegment(userId)}/block`)
    },
    getBlockList(params?: PaginationParams, config?: AxiosRequestConfig) {
        if (params) {
            return api.get<ApiResponse<BlockListRawResponse>>('/users/me/blocks', { ...config, params })
                .then((response) => mapApiPageResponse(response, toBlockedUserPage))
        }
        return (config
            ? api.get<ApiResponse<BlockListRawResponse>>('/users/me/blocks', config)
            : api.get<ApiResponse<BlockListRawResponse>>('/users/me/blocks'))
            .then((response) => mapApiPageResponse(response, toBlockedUserPage))
    },
    getMyPosts(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>('/users/me/posts', { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage))
    },
    getMyComments(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<MyComment>>>('/users/me/comments', { ...config, params })
    },
    getPublicUserPosts(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>(`/users/${encodePathSegment(userId)}/posts`, { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage))
    },
    getPublicUserComments(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<MyComment>>>(`/users/${encodePathSegment(userId)}/comments`, { ...config, params })
    },
    getMyScraps(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<ScrapListResponse>>('/users/me/scraps', { ...config, params })
            .then((response) => mapApiPageResponse(response, toScrapPostSummaryPage))
    },
    getScrapFolders(config?: AxiosRequestConfig) {
        return api.get<ApiResponse<ScrapFolder[]>>('/users/me/scrap-folders', config)
    },
    createScrapFolder(payload: ScrapFolderPayload, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<ScrapFolder>>('/users/me/scrap-folders', payload, config)
    },
    updateScrapFolder(folderId: string | number, payload: Partial<ScrapFolderPayload>, config?: AxiosRequestConfig) {
        return api.patch<ApiResponse<ScrapFolder>>(`/users/me/scrap-folders/${encodePathSegment(folderId)}`, payload, config)
    },
    deleteScrapFolder(folderId: string | number, config?: AxiosRequestConfig) {
        return api.delete<ApiResponse<void>>(`/users/me/scrap-folders/${encodePathSegment(folderId)}`, config)
    },
    getPostSeries(config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PostSeries[]>>('/users/me/post-series', config)
    },
    createPostSeries(payload: PostSeriesPayload, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<PostSeries>>('/users/me/post-series', payload, config)
    },
    updatePostSeries(seriesId: string | number, payload: PostSeriesPayload) {
        return api.patch<ApiResponse<PostSeries>>(`/users/me/post-series/${encodePathSegment(seriesId)}`, payload)
    },
    deletePostSeries(seriesId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/me/post-series/${encodePathSegment(seriesId)}`)
    },
    getMyDrafts(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<DraftPostListResponse>>('/users/me/drafts', { ...config, params })
    },
    getRecentlyViewedPosts(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>('/users/me/history/views', { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage))
    },
    getMySubscriptions(params: SubscriptionParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<SubscriptionBoardListItem>>>('/users/me/subscriptions', { ...config, params })
    },
    getMyPoint() {
        return api.get<ApiResponse<UserPoint>>('/points/me')
    },
    getMyPointHistories(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PointHistoryResponse>>('/points/me/history', { ...config, params })
            .then((response) => mapApiPageResponse<PointHistoryResponse, PointHistory>(
                response,
                (source) => normalizePageResponse(source as PageResponseRaw<PointHistory>)
            ))
    },
}
