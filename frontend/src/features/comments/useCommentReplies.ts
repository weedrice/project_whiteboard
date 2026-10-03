import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { Comment } from '@/api/comment'
import { useComment } from '@/features/comments/queries/useComment'

export function useCommentReplies(
  comment: ComputedRef<Comment>,
  postId: Readonly<Ref<string | number>>,
  deepLinkCommentIds?: ComputedRef<readonly number[]>,
) {
  const { useInfiniteReplies } = useComment()

  const isRepliesOpen = ref(false)
  const openedForDeepLink = ref(false)
  const optimisticHasReplies = ref(false)
  const replyParams = ref({ size: 50 })
  const commentId = computed(() => comment.value.commentId)
  const deepLinkChildId = computed(() => {
    const path = deepLinkCommentIds?.value ?? []
    const currentIndex = path.indexOf(commentId.value)
    return currentIndex >= 0 ? path[currentIndex + 1] : undefined
  })
  const canLoadReplies = computed(() => Boolean(comment.value.hasReplies || optimisticHasReplies.value))
  const repliesEnabled = computed(() => isRepliesOpen.value && canLoadReplies.value)

  const {
    data: repliesData,
    isLoading: isRepliesLoading,
    isFetching: isRepliesFetching,
    error: repliesError,
    hasNextPage: replyHasNext,
    fetchNextPage,
    refetch: refetchReplies,
  } = useInfiniteReplies(postId, commentId, replyParams, repliesEnabled)

  // Every loaded page remains in the active query. Invalidations and optimistic
  // updates therefore replace earlier pages as well as the most recent page.
  const replies = computed(() => repliesData.value?.pages.flatMap((page) => page.content) ?? [])

  function markReplyCreated() {
    optimisticHasReplies.value = true
    openedForDeepLink.value = false
    isRepliesOpen.value = true
  }

  function toggleReplies() {
    openedForDeepLink.value = false
    isRepliesOpen.value = !isRepliesOpen.value
  }

  function loadMoreReplies() {
    if (!replyHasNext.value || isRepliesFetching.value) return
    void fetchNextPage()
  }

  watch([replies, deepLinkChildId, replyHasNext, isRepliesFetching, isRepliesOpen], () => {
    if (
      isRepliesOpen.value
      && !repliesError.value
      && deepLinkChildId.value !== undefined
      && !replies.value.some((reply) => reply.commentId === deepLinkChildId.value)
    ) {
      loadMoreReplies()
    }
  })

  watch(commentId, () => {
    optimisticHasReplies.value = false
    openedForDeepLink.value = false
    isRepliesOpen.value = false
  })

  watch(() => comment.value.hasReplies, (hasReplies) => {
    if (hasReplies || optimisticHasReplies.value) return

    openedForDeepLink.value = false
    isRepliesOpen.value = false
  })

  watch(deepLinkChildId, (childId) => {
    if (childId !== undefined) {
      openedForDeepLink.value = true
      isRepliesOpen.value = true
    } else if (openedForDeepLink.value) {
      openedForDeepLink.value = false
      isRepliesOpen.value = false
    }
  }, { immediate: true })

  return {
    replies,
    isRepliesOpen,
    isRepliesLoading,
    repliesError,
    replyHasNext,
    canLoadReplies,
    markReplyCreated,
    toggleReplies,
    loadMoreReplies,
    refetchReplies,
  }
}
