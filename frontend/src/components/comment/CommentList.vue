<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { useComment } from '@/composables/useComment'
import { useAuthStore } from '@/stores/auth'
import { useConfirm } from '@/composables/useConfirm'
import logger from '@/utils/logger'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'

const { confirm } = useConfirm()

const props = defineProps<{
  postId: number | string
  boardUrl: string
  lastViewedAt?: string | null
  pendingCommentCount?: number
}>()

const emit = defineEmits<{
  (event: 'refresh-comments'): void
  (event: 'last-read-comment', commentId: number): void
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const commentListRoot = ref<HTMLElement | null>(null)
const observedCommentElements = new WeakSet<Element>()
let readObserver: IntersectionObserver | null = null
let commentMutationObserver: MutationObserver | null = null
const { useBestComments, useInfiniteComments, useDeleteComment } = useComment()

const COMMENT_PAGE_INCREMENT = 50
const sort = ref('createdAt,asc')
const params = computed(() => ({ page: 0, size: COMMENT_PAGE_INCREMENT, sort: sort.value }))
const postId = computed(() => props.postId)
const {
  data: commentsData,
  isLoading,
  isFetchingNextPage,
  hasNextPage,
  fetchNextPage,
  error: commentsError,
  refetch: refetchComments,
} = useInfiniteComments(postId, params)
const { data: bestCommentsData } = useBestComments(postId)
const comments = computed<Comment[]>(() => commentsData.value?.pages.flatMap((page) => page.content) || [])
const bestComments = computed<Comment[]>(() => bestCommentsData.value || [])
const totalCommentCount = computed(() => commentsData.value?.pages[0]?.totalElements ?? comments.value.length)
const hasMoreComments = computed(() => Boolean(hasNextPage.value))
const commentLoadFailedMessage = computed(() => t('comment.loadFailed'))
const sortOptions = computed(() => [
  { value: 'createdAt,asc', label: t('comment.sort.oldest') },
  { value: 'createdAt,desc', label: t('comment.sort.newest') },
  { value: 'likeCount,desc', label: t('comment.sort.likes') },
])
const lastViewedTime = computed(() => {
  if (!props.lastViewedAt) {
    return null
  }

  const time = new Date(props.lastViewedAt).getTime()
  return Number.isNaN(time) ? null : time
})
const isOldestSort = computed(() => sort.value === 'createdAt,asc')
const firstUnreadCommentId = computed(() => {
  if (!isOldestSort.value || lastViewedTime.value == null) {
    return null
  }

  return comments.value.find((comment) => isNewComment(comment))?.commentId ?? null
})
const loadMoreCommentsLabel = computed(() => t('comment.loadMore', {
  remaining: Math.max(totalCommentCount.value - comments.value.length, 0)
}))

const { mutate: deleteComment } = useDeleteComment()

async function handleDelete(comment: Comment) {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) {
    return
  }

  deleteComment({ commentId: comment.commentId, postId: props.postId }, {
    onError: (err) => {
      logger.error('Failed to delete comment:', err)
    },
  })
}

function loadMoreComments() {
  void fetchNextPage()
}

function isNewComment(comment: Comment) {
  if (lastViewedTime.value == null) {
    return false
  }

  const createdAt = new Date(comment.createdAt).getTime()
  return !Number.isNaN(createdAt) && createdAt > lastViewedTime.value
}

function observeCommentElements() {
  if (!readObserver || !commentListRoot.value) return

  commentListRoot.value.querySelectorAll<HTMLElement>('[data-comment-id]').forEach((element) => {
    if (observedCommentElements.has(element)) return
    observedCommentElements.add(element)
    readObserver?.observe(element)
  })
}

watch([comments, bestComments], () => {
  void nextTick(observeCommentElements)
})

onMounted(() => {
  if (typeof IntersectionObserver === 'undefined') return

  readObserver = new IntersectionObserver((entries) => {
    const latestVisibleEntry = entries.filter((entry) => entry.isIntersecting).at(-1)
    const rawCommentId = (latestVisibleEntry?.target as HTMLElement | undefined)?.dataset.commentId
    const commentId = Number(rawCommentId)
    if (Number.isInteger(commentId) && commentId > 0) {
      emit('last-read-comment', commentId)
    }
  }, {
    rootMargin: '-20% 0px -60% 0px',
    threshold: 0,
  })

  if (typeof MutationObserver !== 'undefined' && commentListRoot.value) {
    commentMutationObserver = new MutationObserver(observeCommentElements)
    commentMutationObserver.observe(commentListRoot.value, { childList: true, subtree: true })
  }

  observeCommentElements()
})

onBeforeUnmount(() => {
  readObserver?.disconnect()
  readObserver = null
  commentMutationObserver?.disconnect()
  commentMutationObserver = null
})
</script>

<template>
  <div ref="commentListRoot" class="mt-6 sm:mt-8">
    <h3 class="mb-4 text-base font-medium nv-title sm:mb-6 sm:text-lg">
      {{ $t('comment.title') }}
    </h3>

    <div
      id="comment-composer"
      class="mb-6 rounded-[24px] border border-[var(--nv-line)] bg-[color:var(--nv-surface)] px-4 py-4 sm:mb-8 sm:px-5"
    >
      <div v-if="authStore.isAuthenticated">
        <CommentForm :postId="postId" />
      </div>
      <div v-else class="text-xs nv-text-subtle sm:text-sm">
        <router-link
          to="/login"
          class="nv-accent-text underline underline-offset-2 hover:brightness-95"
        >
          {{ $t('common.login') }}
        </router-link>
        {{ $t('comment.loginRequired', { login: '' }) }}
      </div>
    </div>

    <div v-if="isLoading" class="space-y-4 sm:space-y-6">
      <div v-for="i in 3" :key="i" class="flex space-x-3">
        <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" />
        <div class="flex-1 space-y-2">
          <div class="flex items-center justify-between">
            <BaseSkeleton width="100px" height="16px" />
            <BaseSkeleton width="60px" height="14px" />
          </div>
          <BaseSkeleton width="100%" height="16px" />
          <BaseSkeleton width="80%" height="16px" />
        </div>
      </div>
    </div>

    <ErrorState
      v-else-if="commentsError"
      title-tag="h4"
      :message="commentLoadFailedMessage"
      show-retry
      @retry="refetchComments()"
    />

    <div v-else class="space-y-4 sm:space-y-6">
      <div class="flex justify-end">
        <BaseSegmentedControl
          v-model="sort"
          :options="sortOptions"
          :label="$t('comment.sort.label')"
          variant="joined"
        />
      </div>

      <div v-if="props.pendingCommentCount && props.pendingCommentCount > 0" class="sticky top-4 z-10 flex justify-center">
        <BaseButton type="button" size="sm" @click="emit('refresh-comments')">
          {{ $t('comment.newArrivals', { count: props.pendingCommentCount }) }}
        </BaseButton>
      </div>

      <section v-if="bestComments.length > 0" class="space-y-3 rounded-md border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4">
        <h4 class="text-sm font-semibold nv-title">{{ $t('comment.best.title') }}</h4>
        <CommentItem
          v-for="comment in bestComments"
          :key="`best-${comment.commentId}`"
          :comment="comment"
          :postId="postId"
          :boardUrl="boardUrl"
          @delete="handleDelete"
        />
      </section>

      <template
        v-for="comment in comments"
        :key="comment.commentId"
      >
        <div
          v-if="firstUnreadCommentId === comment.commentId"
          class="nv-comment-read-divider"
        >
          <span>{{ $t('comment.readUntilHere') }}</span>
        </div>
        <CommentItem
          v-memo="[postId, boardUrl, props.lastViewedAt, comment.commentId, comment.content, comment.likeCount, comment.createdAt, comment.isDeleted, comment.isBlinded, comment.isBlockedAuthor, comment.maskedAuthorId, comment.replyCount, comment.hasReplies]"
          :comment="comment"
          :postId="postId"
          :boardUrl="boardUrl"
          :is-new-since-last-view="isNewComment(comment)"
          @delete="handleDelete"
        />
      </template>

      <div
        v-if="comments.length === 0"
        class="py-3 text-center text-xs nv-text-subtle sm:py-4 sm:text-sm"
      >
        {{ $t('comment.empty') }}
      </div>

      <div v-else-if="hasMoreComments" class="flex justify-center pt-2">
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          :loading="isFetchingNextPage"
          @click="loadMoreComments"
        >
          {{ loadMoreCommentsLabel }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nv-comment-read-divider {
  align-items: center;
  color: var(--nv-accent);
  display: flex;
  font-family: var(--font-mono);
  font-size: 0.72rem;
  font-weight: 700;
  gap: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nv-comment-read-divider::before,
.nv-comment-read-divider::after {
  background: color-mix(in srgb, var(--nv-accent) 45%, var(--nv-line));
  content: "";
  flex: 1;
  height: 1px;
}
</style>
