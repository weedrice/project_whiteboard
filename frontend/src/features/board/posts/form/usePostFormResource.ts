import { computed, type Ref } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/features/board/posts/queries/usePost'

type UsePostFormResourceOptions = {
    mode: () => 'create' | 'edit'
    boardUrl: Ref<string>
    postId: Ref<string | number>
    skipBoardLookup: () => boolean | undefined
    hideNotice: () => boolean | undefined
}

export function usePostFormResource(options: UsePostFormResourceOptions) {
    const { useBoardDetail } = useBoard()
    const {
        usePostDetail,
        useCreatePost,
        useUpdatePost,
    } = usePost()

    const queryEnabled = computed(() => !!options.boardUrl.value && !options.skipBoardLookup())
    const { data: board, isLoading: isBoardLoading } = useBoardDetail(options.boardUrl, {
        enabled: queryEnabled,
    })
    const categories = computed(() => board.value?.categories ?? [])
    const postIdRef = computed(() => (options.mode() === 'edit' ? options.postId.value : '') as string)
    const { data: post, isLoading: isPostLoading } = usePostDetail(postIdRef, {
        enabled: computed(() => options.mode() === 'edit' && !!options.postId.value),
        requestConfig: { params: { incrementView: false } },
    })
    const { mutate: createPost, isPending: isCreateSubmitting } = useCreatePost()
    const { mutate: updatePost, isPending: isUpdateSubmitting } = useUpdatePost()

    const isSubmitting = computed(() => isCreateSubmitting.value || isUpdateSubmitting.value)
    const isLoading = computed(() =>
        isBoardLoading.value || (options.mode() === 'edit' && isPostLoading.value),
    )
    const showNotice = computed(() =>
        !options.hideNotice() && options.mode() === 'create' && Boolean(board.value?.isAdmin),
    )
    const canShowNsfw = computed(() => Boolean(board.value?.allowNsfw))

    return {
        board,
        categories,
        post,
        isLoading,
        isSubmitting,
        showNotice,
        canShowNsfw,
        createPost,
        updatePost,
    }
}
