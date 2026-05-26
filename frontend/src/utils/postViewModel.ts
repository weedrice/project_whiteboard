import type { FeedPost, Post, PostSummary } from '@/types'

export type PostReactionAlias = Post & {
    isLiked?: boolean
    isScrapped?: boolean
}

export function normalizePostReactionFlags(post: PostReactionAlias): Post {
    return {
        ...post,
        liked: post.liked ?? post.isLiked ?? false,
        scrapped: post.scrapped ?? post.isScrapped ?? false,
    }
}

export function toFeedPost(post: PostSummary): FeedPost | null {
    if (
        post.postId == null ||
        post.boardUrl == null ||
        post.boardName == null ||
        (post.authorName == null && post.author?.displayName == null)
    ) {
        return null
    }

    return {
        ...post,
        boardUrl: post.boardUrl,
        boardName: post.boardName,
        boardIconUrl: post.boardIconUrl,
        authorName: post.authorName ?? post.author?.displayName ?? '',
        liked: post.liked ?? false,
        scrapped: post.scrapped ?? false,
        subscribed: post.subscribed ?? false,
    }
}

export function toFeedPosts(posts: PostSummary[] | undefined): FeedPost[] {
    return (posts ?? [])
        .map(toFeedPost)
        .filter((post): post is FeedPost => post != null)
}
