export const postQueryKeys = {
    detailsRoot: ['post'] as const,
    detailPrefix: (postId: string | number) => ['post', postId] as const,
    detail: (
        postId: string | number,
        incrementView = true,
    ) => ['post', postId, { incrementView }] as const,
    related: (postId: string | number, size: number) =>
        ['post', postId, 'related', size] as const,
    versions: (postId: string | number) =>
        ['post', postId, 'versions'] as const,
    lists: ['posts'] as const,
    boardPostsRoot: ['board', 'posts'] as const,
    boardPosts: (boardUrl: string) => ['board', 'posts', boardUrl] as const,
}

export const postDetailQueryKey = (
    postId: string | number,
    incrementView = true,
) => postQueryKeys.detail(postId, incrementView)
