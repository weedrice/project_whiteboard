import { computed, ref, watch, type ComputedRef } from 'vue'
import type { Comment } from '@/api/comment'
import { useComment } from '@/composables/useComment'

export function useCommentReplies(comment: ComputedRef<Comment>) {
  const { useReplies } = useComment()

  const isRepliesOpen = ref(!comment.value.isDeleted && Boolean(comment.value.hasReplies))
  const optimisticHasReplies = ref(false)
  const replyParams = ref({ page: 0, size: 50 })
  const loadedReplies = ref<Comment[]>([])
  const replyHasNext = ref(false)
  const commentId = computed(() => comment.value.commentId)
  const canLoadReplies = computed(() => !comment.value.isDeleted && Boolean(comment.value.hasReplies || optimisticHasReplies.value))
  const repliesEnabled = computed(() => isRepliesOpen.value && canLoadReplies.value)

  const { data: repliesData, isLoading: isRepliesLoading, error: repliesError, refetch: refetchReplies } =
    useReplies(commentId, replyParams, repliesEnabled)

  const replies = computed(() => loadedReplies.value)

  function markReplyCreated() {
    optimisticHasReplies.value = true
    isRepliesOpen.value = true
    replyParams.value = { ...replyParams.value, page: 0 }
  }

  function toggleReplies() {
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

    if (replyParams.value.page === 0) {
      loadedReplies.value = pageData.content
    } else {
      const existingIds = new Set(loadedReplies.value.map((reply) => reply.commentId))
      loadedReplies.value = [
        ...loadedReplies.value,
        ...pageData.content.filter((reply) => !existingIds.has(reply.commentId)),
      ]
    }

    replyHasNext.value = pageData.hasNext

    if (!comment.value.hasReplies && !optimisticHasReplies.value && loadedReplies.value.length === 0) {
      optimisticHasReplies.value = false
      isRepliesOpen.value = false
    }
  }, { immediate: true })

  watch(() => comment.value.hasReplies, (hasReplies) => {
    if (!hasReplies && !optimisticHasReplies.value) {
      optimisticHasReplies.value = false
      loadedReplies.value = []
      replyHasNext.value = false
      isRepliesOpen.value = false
      return
    }

    if (hasReplies && !comment.value.isDeleted) {
      isRepliesOpen.value = true
    }
  })

  watch(() => comment.value.isDeleted, (isDeleted) => {
    if (!isDeleted) {
      return
    }

    optimisticHasReplies.value = false
    loadedReplies.value = []
    replyHasNext.value = false
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
