import { computed, ref, watch, type ComputedRef } from 'vue'
import type { Comment } from '@/api/comment'
import { useComment } from '@/features/comments/queries/useComment'

export function useCommentReplies(
  comment: ComputedRef<Comment>,
  deepLinkCommentIds?: ComputedRef<readonly number[]>,
) {
  const { useReplies } = useComment()

  const isRepliesOpen = ref(false)
  const openedForDeepLink = ref(false)
  const optimisticHasReplies = ref(false)
  const replyParams = ref({ page: 0, size: 50 })
  const loadedReplies = ref<Comment[]>([])
  const replyHasNext = ref(false)
  const commentId = computed(() => comment.value.commentId)
  const deepLinkChildId = computed(() => {
    const path = deepLinkCommentIds?.value ?? []
    const currentIndex = path.indexOf(commentId.value)
    return currentIndex >= 0 ? path[currentIndex + 1] : undefined
  })
  const canLoadReplies = computed(() => !comment.value.isDeleted && Boolean(comment.value.hasReplies || optimisticHasReplies.value))
  const repliesEnabled = computed(() => isRepliesOpen.value && canLoadReplies.value)

  const { data: repliesData, isLoading: isRepliesLoading, error: repliesError, refetch: refetchReplies } =
    useReplies(commentId, replyParams, repliesEnabled)

  const replies = computed(() => loadedReplies.value)

  function markReplyCreated() {
    optimisticHasReplies.value = true
    openedForDeepLink.value = false
    isRepliesOpen.value = true
    replyParams.value = { ...replyParams.value, page: 0 }
  }

  function toggleReplies() {
    openedForDeepLink.value = false
    isRepliesOpen.value = !isRepliesOpen.value
  }

  function loadMoreReplies() {
    if (!replyHasNext.value || isRepliesLoading.value) {
      return
    }

    replyParams.value = {
      ...replyParams.value,
      page: replyParams.value.page + 1,
    }
  }

  watch(repliesData, (pageData) => {
    if (!pageData) {
      return
    }

    const responsePage = pageData.page
    if (responsePage !== replyParams.value.page) return
    if (responsePage === 0) {
      loadedReplies.value = pageData.content
    } else {
      const existingIds = new Set(loadedReplies.value.map((reply) => reply.commentId))
      loadedReplies.value = [
        ...loadedReplies.value,
        ...pageData.content.filter((reply) => !existingIds.has(reply.commentId)),
      ]
    }

    replyHasNext.value = pageData.hasNext

    if (
      deepLinkChildId.value !== undefined
      && !loadedReplies.value.some((reply) => reply.commentId === deepLinkChildId.value)
      && replyHasNext.value
    ) {
      loadMoreReplies()
    }

    if (!comment.value.hasReplies && !optimisticHasReplies.value && loadedReplies.value.length === 0) {
      optimisticHasReplies.value = false
      isRepliesOpen.value = false
    }
  }, { immediate: true })

  watch(commentId, () => {
    optimisticHasReplies.value = false
    replyParams.value = { ...replyParams.value, page: 0 }
    loadedReplies.value = []
    replyHasNext.value = false
    openedForDeepLink.value = false
    isRepliesOpen.value = false
  })

  watch(() => comment.value.hasReplies, (hasReplies) => {
    if (hasReplies || optimisticHasReplies.value) return

    optimisticHasReplies.value = false
    loadedReplies.value = []
    replyHasNext.value = false
    openedForDeepLink.value = false
    isRepliesOpen.value = false
  })

  watch(deepLinkChildId, (childId) => {
    if (childId !== undefined && !comment.value.isDeleted) {
      openedForDeepLink.value = true
      isRepliesOpen.value = true
    } else if (openedForDeepLink.value) {
      openedForDeepLink.value = false
      isRepliesOpen.value = false
    }
  }, { immediate: true })

  watch(() => comment.value.isDeleted, (isDeleted) => {
    if (!isDeleted) {
      return
    }

    optimisticHasReplies.value = false
    loadedReplies.value = []
    replyHasNext.value = false
    openedForDeepLink.value = false
    isRepliesOpen.value = false
  })

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
