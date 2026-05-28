import api from '@/api'
import type {
    ApiResponse,
    DraftPostListResponse,
    PageResponse,
    User,
    UserSummary,
    UserSettings,
    PostSummary,
    MyComment,
    SubscriptionBoardListItem,
    PublicUserProfile,
    PointHistoryResponse,
    PointHistory,
    ScrapListResponse,
    UserPoint,
} from '@/types'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'

export interface UserUpdatePayload {
    displayName?: string;
    profileImageId?: number | null;
}

interface PaginationParams {
    page?: number
    size?: number
    sort?: string
}

interface SubscriptionParams extends PaginationParams {
    includeUnavailable?: boolean
}

export interface BlockedUserSummary extends UserSummary {
    email?: string
}

export type NotificationSettingType = 'LIKE' | 'COMMENT' | 'REPLY'

export interface NotificationSettingsPayload {
    notificationType: NotificationSettingType
    isEnabled: boolean
}

export interface NotificationSettingsBulkPayload {
    settings: NotificationSettingsPayload[]
}

export interface UserAgent {
    agentId: number
    name: string
    description: string
    status: 'PENDING_CLAIM' | 'ACTIVE' | 'SUSPENDED'
    createdAt: string
    claimedAt?: string
}

export interface UserAgentListResponse {
    agents: UserAgent[]
}

export function toScrapPostSummaryPage(response: ScrapListResponse): PageResponse<PostSummary> {
    return normalizePageResponse({
        ...response,
        content: response.content.map(({ post }) => ({
            postId: post.postId,
            title: post.title,
            viewCount: post.viewCount,
            likeCount: post.likeCount,
            commentCount: post.commentCount ?? 0,
            isNotice: post.isNotice ?? false,
            isNsfw: post.isNsfw ?? false,
            isSpoiler: post.isSpoiler ?? false,
            isSecret: post.isSecret ?? false,
            thumbnailUrl: post.thumbnailUrl ?? undefined,
            firstMediaType: post.firstMediaType ?? undefined,
            author: post.author,
            createdAt: post.createdAt,
            rowNum: post.rowNum,
            boardName: post.boardName,
            boardUrl: post.boardUrl,
            scrapped: true,
        })),
    })
}

export function toBlockedUserSummaryPage(
    response: PageResponseRaw<BlockedUserSummary> | BlockedUserSummary[],
): PageResponse<BlockedUserSummary> {
    if (Array.isArray(response)) {
        return normalizePageResponse({
            content: response,
            totalElements: response.length,
            totalPages: response.length > 0 ? 1 : 0,
            size: response.length,
        })
    }

    return normalizePageResponse(response)
}

function mapApiPageResponse<TSource, TTarget>(
    response: AxiosResponse<ApiResponse<TSource>>,
    mapper: (source: TSource) => PageResponse<TTarget>,
): AxiosResponse<ApiResponse<PageResponse<TTarget>>> {
    if (!response.data.success || response.data.data == null) {
        return response as unknown as AxiosResponse<ApiResponse<PageResponse<TTarget>>>
    }

    return {
        ...response,
        data: {
            ...response.data,
            data: mapper(response.data.data),
        },
    }
}

export const userApi = {
    getMyProfile() {
        return api.get<ApiResponse<User>>('/users/me')
    },
    getUserProfile(userId: string | number) {
        return api.get<ApiResponse<PublicUserProfile>>(`/users/${userId}`)
    },
    updateMyProfile(data: UserUpdatePayload) {
        return api.put<ApiResponse<User>>('/users/me', data)
    },
    updatePassword(currentPassword: string, newPassword: string) {
        return api.put<ApiResponse<void>>('/users/me/password', { currentPassword, newPassword })
    },
    deleteAccount(password: string) {
        return api.delete<ApiResponse<void>>('/users/me', { data: { password } })
    },
    verifyEmail(payload: { email: string, verificationTicket: string }) {
        return api.post<ApiResponse<void>>('/users/me/email-verification', payload)
    },
    getUserSettings() {
        return api.get<ApiResponse<UserSettings>>('/users/me/settings')
    },
    updateUserSettings(data: Partial<UserSettings>) {
        return api.put<ApiResponse<UserSettings>>('/users/me/settings', data)
    },
    getNotificationSettings() {
        return api.get<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings')
    },
    updateNotificationSettingsBulk(data: NotificationSettingsBulkPayload) {
        return api.put<ApiResponse<NotificationSettingsPayload[]>>('/users/me/notification-settings/bulk', data)
    },
    claimAgent(agentToken: string) {
        return api.post<ApiResponse<UserAgent>>('/users/me/agents/claim', { agentToken })
    },
    getMyAgents() {
        return api.get<ApiResponse<UserAgentListResponse>>('/users/me/agents')
    },
    suspendMyAgent(agentId: string | number) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${agentId}/suspend`)
    },
    activateMyAgent(agentId: string | number) {
        return api.patch<ApiResponse<UserAgent>>(`/users/me/agents/${agentId}/activate`)
    },
    deleteMyAgent(agentId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/me/agents/${agentId}`)
    },
    blockUser(userId: string | number) {
        return api.post<ApiResponse<void>>(`/users/${userId}/block`)
    },
    unblockUser(userId: string | number) {
        return api.delete<ApiResponse<void>>(`/users/${userId}/block`)
    },
    getBlockList(params?: PaginationParams) {
        const request = params
            ? api.get<ApiResponse<PageResponse<BlockedUserSummary> | BlockedUserSummary[]>>('/users/me/blocks', { params })
            : api.get<ApiResponse<PageResponse<BlockedUserSummary> | BlockedUserSummary[]>>('/users/me/blocks')

        return request.then((response) => mapApiPageResponse(response, toBlockedUserSummaryPage))
    },
    getMyPosts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/posts', { params })
    },
    getMyComments(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<MyComment>>>('/users/me/comments', { params })
    },
    getMyScraps(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<ScrapListResponse>>('/users/me/scraps', { ...config, params })
            .then((response) => mapApiPageResponse(response, toScrapPostSummaryPage))
    },
    getMyDrafts(params: PaginationParams) {
        return api.get<ApiResponse<DraftPostListResponse>>('/users/me/drafts', { params })
    },
    getRecentlyViewedPosts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/history/views', { params })
    },
    getMySubscriptions(params: SubscriptionParams) {
        return api.get<ApiResponse<PageResponse<SubscriptionBoardListItem>>>('/users/me/subscriptions', { params })
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
    }
}
