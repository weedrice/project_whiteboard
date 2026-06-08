import type { Ref } from 'vue'
import type { CommentParams } from '@/api/comment'

export const commentQueryKeys = {
    all: ['comments'] as const,
    list: (postId: Ref<string | number>, params: Ref<CommentParams>) => ['comments', postId, params] as const,
    replies: (parentId: Ref<string | number>, params: Ref<CommentParams>) =>
        ['comments', 'replies', parentId, params] as const,
}
