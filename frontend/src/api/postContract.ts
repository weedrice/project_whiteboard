import type { BoardDetail, PageResponse, Post, PostCategorySummary, PostSummary } from '@/types'
import { normalizePageResponseItems, type PageResponseRaw } from '@/utils/pageResponse'

export interface PostSummaryWire extends Omit<PostSummary, 'category' | 'isNotice' | 'isNsfw'> {
    category?: PostCategorySummary | null
    notice?: boolean
    nsfw?: boolean
    isNotice?: boolean
    isNsfw?: boolean
}

export interface PostDetailWire extends Omit<Post, 'category' | 'liked' | 'scrapped'> {
    category?: PostCategorySummary | null
    liked?: boolean
    scrapped?: boolean
    isLiked?: boolean
    isScrapped?: boolean
}

export type BoardDetailWire = Omit<BoardDetail, 'latestPosts'> & {
    latestPosts: PostSummaryWire[]
}

export function normalizePostSummary(post: PostSummaryWire): PostSummary {
    return {
        ...post,
        category: post.category ?? undefined,
        isNotice: post.isNotice ?? post.notice ?? false,
        isNsfw: post.isNsfw ?? post.nsfw ?? false,
    }
}

export function normalizePostSummaryList(posts: PostSummaryWire[] | null | undefined): PostSummary[] {
    return (posts ?? []).map(normalizePostSummary)
}

export function normalizePostSummaryPage(
    page: PageResponseRaw<PostSummaryWire> | PageResponse<PostSummaryWire> | null | undefined,
): PageResponse<PostSummary> {
    return normalizePageResponseItems(page, normalizePostSummary)
}

export function normalizePostDetail(post: PostDetailWire): Post {
    return {
        ...post,
        category: post.category ?? undefined,
        liked: post.liked ?? post.isLiked ?? false,
        scrapped: post.scrapped ?? post.isScrapped ?? false,
    }
}

export function normalizeBoardDetail(board: BoardDetailWire): BoardDetail {
    return {
        ...board,
        latestPosts: normalizePostSummaryList(board.latestPosts),
    }
}
