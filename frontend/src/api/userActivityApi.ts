import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    DraftPostListResponse,
    MyComment,
    PageResponse,
    PointHistory,
    PointHistoryResponse,
    PostSummary,
    ScrapListResponse,
    SubscriptionBoardListItem,
    UserPoint,
} from '@/types'
import { mapApiPageResponse } from '@/api/response'
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
}

interface SubscriptionParams extends PaginationParams {
    includeUnavailable?: boolean
}

export const userActivityApi = {
    blockUser(userId: string | number) {
        return api.post<ApiResponse<void>>(`/users/${encodePathSegment(userId)}/block`)
    },
    unblockUser(userId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/${encodePathSegment(userId)}/block`)
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
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/posts', { ...config, params })
    },
    getMyComments(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<MyComment>>>('/users/me/comments', { ...config, params })
    },
    getMyScraps(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<ScrapListResponse>>('/users/me/scraps', { ...config, params })
            .then((response) => mapApiPageResponse(response, toScrapPostSummaryPage))
    },
    getMyDrafts(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<DraftPostListResponse>>('/users/me/drafts', { ...config, params })
    },
    getRecentlyViewedPosts(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/history/views', { ...config, params })
    },
    getMySubscriptions(params: SubscriptionParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<SubscriptionBoardListItem>>>('/users/me/subscriptions', { ...config, params })
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
