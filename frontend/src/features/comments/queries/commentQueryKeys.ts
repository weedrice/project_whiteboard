import type { CommentParams } from '@/api/comment'

export const commentQueryKeys = {
    all: ['comments'] as const,
    postRoot: (postId: string | number) => ['comments', 'post', postId] as const,
    list: (postId: string | number, params: Readonly<CommentParams>) =>
        [...commentQueryKeys.postRoot(postId), { ...params }] as const,
    repliesRoot: ['comments', 'replies'] as const,
    repliesByPost: (postId: string | number) =>
        [...commentQueryKeys.repliesRoot, 'post', String(postId)] as const,
    infiniteReplies: (postId: string | number, parentId: string | number, params: Readonly<Omit<CommentParams, 'page'>>) =>
        [...commentQueryKeys.repliesByPost(postId), parentId, 'infinite', { ...params }] as const,
    replies: (parentId: string | number, params: Readonly<CommentParams>) =>
        [...commentQueryKeys.repliesRoot, parentId, { ...params }] as const,
}
