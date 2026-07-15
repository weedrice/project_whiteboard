import type { Ref } from 'vue'
import type { CommentParams } from '@/api/comment'

export const commentQueryKeys = {
    all: ['comments'] as const,
    postRoot: (postId: string | number | Ref<string | number>) => ['comments', 'post', postId] as const,
    list: (postId: Ref<string | number>, params: Ref<CommentParams>) =>
        [...commentQueryKeys.postRoot(postId), params] as const,
    replies: (parentId: Ref<string | number>, params: Ref<CommentParams>) =>
        ['comments', 'replies', parentId, params] as const,
}
