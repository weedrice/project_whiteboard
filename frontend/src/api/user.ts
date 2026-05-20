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
import type { AxiosResponse } from 'axios'

export interface UserProfile {
    userId: number;
    displayName: string;
    email: string;
    profileImageUrl?: string;
    role: 'USER' | 'ADMIN' | 'SUPER_ADMIN';
    createdAt: string;
}

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

function toPageResponse<T>(response: {
    content: T[]
    page?: number
    number?: number
    size: number
    totalElements: number
    totalPages: number
    hasNext?: boolean
    hasPrevious?: boolean
    last?: boolean
    first?: boolean
    empty?: boolean
}): PageResponse<T> {
    const pageNumber = response.number ?? response.page ?? 0
    return {
        content: response.content,
        totalElements: response.totalElements,
        totalPages: response.totalPages,
        size: response.size,
        number: pageNumber,
        first: response.first ?? (pageNumber === 0),
        last: response.last ?? (!response.hasNext),
        empty: response.empty ?? (response.content.length === 0),
    }
}

export function toScrapPostSummaryPage(response: ScrapListResponse): PageResponse<PostSummary> {
    return toPageResponse({
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
    getBlockList() {
        return api.get<ApiResponse<PageResponse<BlockedUserSummary> | BlockedUserSummary[]>>('/users/me/blocks')
    },
    getMyPosts(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/users/me/posts', { params })
    },
    getMyComments(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<MyComment>>>('/users/me/comments', { params })
    },
    getMyScraps(params: PaginationParams) {
        return api.get<ApiResponse<ScrapListResponse>>('/users/me/scraps', { params })
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
    getMyPointHistories(params: PaginationParams) {
        return api.get<ApiResponse<PointHistoryResponse>>('/points/me/history', { params })
            .then((response) => mapApiPageResponse<PointHistoryResponse, PointHistory>(response, toPageResponse))
    }
}
