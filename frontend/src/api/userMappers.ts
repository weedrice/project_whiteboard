import type {
    PageResponse,
    PostSummary,
    ScrapListResponse,
    UserSummary,
} from '@/types'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'

export interface BlockedUserResponseDto extends UserSummary {
    loginId?: string
    blockedAt?: string
    email?: string
}

export interface BlockedUserListItem extends UserSummary {
    loginId?: string
    blockedAt?: string
    secondaryText: string
}

export type BlockListRawResponse = PageResponseRaw<BlockedUserResponseDto> | BlockedUserResponseDto[]

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
            author: post.author,
            createdAt: post.createdAt,
            rowNum: post.rowNum,
            boardName: post.boardName,
            boardUrl: post.boardUrl,
            scrapped: true,
        })),
    })
}

export function toBlockedUserListItem(user: BlockedUserResponseDto): BlockedUserListItem {
    return {
        userId: user.userId,
        agentId: user.agentId,
        authorType: user.authorType,
        displayName: user.displayName,
        profileImageUrl: user.profileImageUrl,
        loginId: user.loginId,
        blockedAt: user.blockedAt,
        secondaryText: user.loginId || user.blockedAt || user.email || '',
    }
}

export function toBlockedUserPage(response: BlockListRawResponse): PageResponse<BlockedUserListItem> {
    if (Array.isArray(response)) {
        return normalizePageResponse({
            content: response.map(toBlockedUserListItem),
            page: 0,
            size: response.length,
            totalElements: response.length,
            totalPages: response.length > 0 ? 1 : 0,
            first: true,
            last: true,
            empty: response.length === 0,
        })
    }

    return normalizePageResponse({
        ...response,
        content: (response.content ?? []).map(toBlockedUserListItem),
    })
}
