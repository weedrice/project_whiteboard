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
    const { data: board, isLoading: isBoardLoading, refetch: refetchBoard } = useBoardDetail(options.boardUrl, {
        enabled: queryEnabled,
    })
    const categories = computed(() => board.value?.categories ?? [])
    const postIdRef = computed(() => (options.mode() === 'edit' ? options.postId.value : '') as string)
    const { data: post, isLoading: isPostLoading, refetch: refetchPost } = usePostDetail(postIdRef, {
        enabled: computed(() => options.mode() === 'edit' && !!options.postId.value),
        requestConfig: { params: { incrementView: false } },
    })
    const isScheduledEdit = computed(() => Boolean(options.scheduledPostId.value))
    const { data: scheduledPost, isLoading: isScheduledPostLoading, refetch: refetchScheduledPost } = useScheduledPostDetail(
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
    // A failed or disabled query also stops loading. Only matching data can
    // authorize editing; an old cached record must not unlock a new identity.
    const isBoardReady = computed(() => Boolean(options.skipBoardLookup() || board.value))
    const isPostReady = computed(() => options.mode() !== 'edit' || isScheduledEdit.value
        || Boolean(post.value && String(post.value.postId) === String(options.postId.value)))
    const isScheduledPostReady = computed(() => !isScheduledEdit.value || Boolean(
        scheduledPost.value
        && String(scheduledPost.value.scheduledPostId) === String(options.scheduledPostId.value)
        && scheduledPost.value.boardUrl === options.boardUrl.value,
    ))
    const isResourceReady = computed(() => isBoardReady.value && isPostReady.value && isScheduledPostReady.value)
    const retryResources = () => Promise.all([
        ...(!isBoardReady.value && queryEnabled.value ? [refetchBoard()] : []),
        ...(!isPostReady.value ? [refetchPost()] : []),
        ...(!isScheduledPostReady.value ? [refetchScheduledPost()] : []),
    ])
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
        isResourceReady,
        retryResources,
        isSubmitting,
        showNotice,
        canShowNsfw,
        createPost,
        createScheduledPost,
        updateScheduledPost,
        updatePost,
    }
}
