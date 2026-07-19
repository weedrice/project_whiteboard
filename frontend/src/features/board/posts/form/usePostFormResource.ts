import { computed, type Ref } from 'vue'
import { useBoard } from '@/features/board/useBoard'
import { usePost } from '@/features/board/posts/queries/usePost'

type UsePostFormResourceOptions = {
    mode: () => 'create' | 'edit'
    boardUrl: Ref<string>
    postId: Ref<string | number>
    scheduledPostId: Ref<string | number>
    skipBoardLookup: () => boolean | undefined
    hideNotice: () => boolean | undefined
}

export function usePostFormResource(options: UsePostFormResourceOptions) {
    const { useBoardDetail } = useBoard()
    const {
        usePostDetail,
        useScheduledPostDetail,
        useCreatePost,
        useCreateScheduledPost,
        useUpdateScheduledPost,
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
    const isScheduledEdit = computed(() => Boolean(options.scheduledPostId.value))
    const { data: scheduledPost, isLoading: isScheduledPostLoading } = useScheduledPostDetail(
        options.scheduledPostId,
        isScheduledEdit,
    )
    const { mutate: createPost, isPending: isCreateSubmitting } = useCreatePost()
    const { mutate: createScheduledPost, isPending: isCreateScheduledSubmitting } = useCreateScheduledPost()
    const { mutate: updateScheduledPost, isPending: isUpdateScheduledSubmitting } = useUpdateScheduledPost()
    const { mutate: updatePost, isPending: isUpdateSubmitting } = useUpdatePost()

    const isSubmitting = computed(() => isCreateSubmitting.value
        || isCreateScheduledSubmitting.value
        || isUpdateScheduledSubmitting.value
        || isUpdateSubmitting.value)
    const isLoading = computed(() =>
        isBoardLoading.value
        || (options.mode() === 'edit' && Boolean(options.postId.value) && isPostLoading.value)
        || (isScheduledEdit.value && isScheduledPostLoading.value),
    )
    const showNotice = computed(() =>
        !options.hideNotice() && Boolean(board.value?.isAdmin),
    )
    const canShowNsfw = computed(() => Boolean(board.value?.allowNsfw))

    return {
        board,
        categories,
        post,
        scheduledPost,
        isLoading,
        isSubmitting,
        showNotice,
        canShowNsfw,
        createPost,
        createScheduledPost,
        updateScheduledPost,
        updatePost,
    }
}
