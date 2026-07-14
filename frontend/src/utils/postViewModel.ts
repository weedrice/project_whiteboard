import type { FeedPost, PostSummary } from '@/types'

export function toFeedPost(post: FeedPost | PostSummary): FeedPost | null {
    const authorName = post.authorName ?? post.author?.displayName
    if (
        post.postId == null ||
        post.boardUrl == null ||
        post.boardName == null ||
        authorName == null
    ) {
        return null
    }

    return {
        ...post,
        boardUrl: post.boardUrl,
        boardName: post.boardName,
        boardIconUrl: post.boardIconUrl,
        authorName,
        liked: post.liked ?? false,
        scrapped: post.scrapped ?? false,
        subscribed: post.subscribed ?? false,
    }
}

export function toFeedPosts(posts: Array<FeedPost | PostSummary> | undefined): FeedPost[] {
    return (posts ?? [])
        .map(toFeedPost)
        .filter((post): post is FeedPost => post != null)
}
