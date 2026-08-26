<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { User as UserIcon, CornerDownRight, Heart } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { useCommentReplies } from '@/features/comments/useCommentReplies'
import { useCommentAuthorState } from '@/features/comments/useCommentAuthorState'
import { useAuthStore } from '@/stores/auth'
import { useComment } from '@/features/comments/queries/useComment'
import { formatDate, formatDateShort } from '@/utils/date'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/features/comments/commentContent'
import type { SanitizedHtml } from '@/utils/sanitize'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import CommentForm from './CommentForm.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import ReportModal from '@/components/report/ReportModal.vue'
import { reportApi } from '@/api/report'
import { useToastStore } from '@/stores/toast'
import { getCurrentSessionGeneration } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'
import { queryClient } from '@/queryClient'
import { invalidateMyReportCaches } from '@/features/user/reports/reportCacheInvalidation'
import type { ReportReasonType } from '@/types'
import { useBadgeTranslation } from '@/features/user/useBadgeTranslation'

defineOptions({
  name: 'CommentItem',
})

const props = withDefaults(defineProps<{
  comment: Comment
  postId: number | string
  boardUrl: string
  depth?: number
  replyTargetName?: string
  isNewSinceLastView?: boolean
  deepLinkCommentIds?: readonly number[]
  readOnly?: boolean
}>(), {
  depth: 0,
  replyTargetName: '',
  isNewSinceLastView: false,
  deepLinkCommentIds: () => [],
  readOnly: false,
})

const emit = defineEmits<{
  (e: 'reply-success'): void
  (e: 'edit-success'): void
  (e: 'delete', comment: Comment): void
}>()

const { t } = useI18n()
const { badgeName } = useBadgeTranslation()
const router = useRouter()
let toastStore: ReturnType<typeof useToastStore> | undefined
try {
  toastStore = useToastStore()
} catch {
  // Pinia 없이 격리 렌더링되는 경우 신고 토스트만 생략한다.
  toastStore = undefined
}
const authStore = useAuthStore() as ReturnType<typeof useAuthStore> | undefined
const { useToggleCommentLike } = useComment()
const { mutate: toggleCommentLike, isPending: isLikePending } = useToggleCommentLike()

const isReplying = ref(false)
const isEditing = ref(false)
const commentRef = computed(() => props.comment)
const {
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
} = useCommentReplies(commentRef, computed(() => props.deepLinkCommentIds))
const {
  isBlockedAuthor,
  canUseCommentActions,
  isAgentAuthor,
  isAuthenticated,
  isCommentAuthor,
} = useCommentAuthorState(commentRef, authStore)
const renderedContent = computed<SanitizedHtml>(() => renderCommentContentHtml(
  props.comment.content ?? '',
  'comment-emoticon',
  props.comment.mentions ?? [],
  t('comment.emoticonAlt')
))
const childReplyTargetName = computed(() => (
  props.comment.author?.displayName?.trim() || t('user.deletedUser')
))
const shouldShowReplyTarget = computed(() => props.depth >= 3 && props.replyTargetName.trim().length > 0)
const isEmoticonOnly = computed(() => isEmoticonOnlyContent(props.comment.content ?? ''))
const isBlinded = computed(() => Boolean(props.comment.isBlinded))
const canReportComment = computed(() => (
  isAuthenticated.value
  && canUseCommentActions.value
  && !isCommentAuthor.value
  && !props.comment.isDeleted
  && !isBlinded.value
))
const isReportModalOpen = ref(false)
let reportAbortController: AbortController | null = null
let reportRevision = 0
const createdAtShort = computed(() => formatDateShort(props.comment.createdAt))
const createdAtFull = computed(() => formatDate(props.comment.createdAt))
const replyToggleLabel = computed(() => {
  if (isRepliesOpen.value) {
    return t('comment.hideReplies')
  }

  return t('comment.viewReplies', { count: props.comment.replyCount })
})

function handleReplySuccess() {
  markReplyCreated()
  isReplying.value = false
  emit('reply-success')
}

function handleEditSuccess() {
  isEditing.value = false
  emit('edit-success')
}

function handleDelete() {
  emit('delete', props.comment)
}

function handleLike() {
  if (!authStore?.isAuthenticated) {
    void router.push({
      name: 'login',
      query: {
        redirect: `/board/${encodeURIComponent(props.boardUrl)}/post/${encodeURIComponent(String(props.postId))}`,
      },
    })
    return
  }
  if (isLikePending.value) return

  toggleCommentLike({
    commentId: props.comment.commentId,
    postId: props.postId,
    liked: !props.comment.liked,
  })
}

function cancelPendingCommentReport() {
  reportRevision += 1
  reportAbortController?.abort()
  reportAbortController = null
}

function openCommentReport() {
  if (!canReportComment.value) return
  cancelPendingCommentReport()
  isReportModalOpen.value = true
}

function closeCommentReport() {
  cancelPendingCommentReport()
  isReportModalOpen.value = false
}

async function submitCommentReport(reason: string, reasonType: ReportReasonType) {
  const commentId = props.comment.commentId
  const sessionGeneration = getCurrentSessionGeneration()
  cancelPendingCommentReport()
  const revision = reportRevision
  const controller = new AbortController()
  reportAbortController = controller
  const isCurrent = () => (
    reportRevision === revision
    && reportAbortController === controller
    && !controller.signal.aborted
    && isReportModalOpen.value
    && props.comment.commentId === commentId
    && getCurrentSessionGeneration() === sessionGeneration
  )

  try {
    const { data } = await reportApi.reportComment(commentId, reason, reasonType, {
      skipGlobalErrorHandler: true,
      signal: controller.signal,
    })
    if (!isCurrent() || !data.success) return false
    void invalidateMyReportCaches(queryClient, sessionGeneration)
    toastStore?.addToast(t('report.reportSuccess'), 'success')
    return true
  } catch (error) {
    if (!isCurrent() || isCancellationError(error)) return false
    toastStore?.addToast(t('report.reportFailed'), 'error')
    return false
  } finally {
    if (reportAbortController === controller) reportAbortController = null
  }
}

function navigateToMention(target: EventTarget | null): boolean {
  const mention = (target as HTMLElement | null)?.closest?.('[data-mention-user-id]')
  if (!(mention instanceof HTMLElement)) return false

  const userId = mention.dataset.mentionUserId
  if (!userId) return false

  void router.push(`/user/${encodeURIComponent(userId)}`)
  return true
}

function handleContentClick(event: MouseEvent) {
  navigateToMention(event.target)
}

function handleContentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return
  if (navigateToMention(event.target)) {
    event.preventDefault()
  }
}

watch(isBlockedAuthor, (blocked) => {
  if (!blocked) {
    return
  }

  isEditing.value = false
  isReplying.value = false
})

watch(isBlinded, (blinded) => {
  if (!blinded) {
    return
  }

  isEditing.value = false
  isReplying.value = false
})

watch(
  [() => props.comment.commentId, canReportComment],
  ([nextCommentId, reportable], [previousCommentId]) => {
    if (!reportable || nextCommentId !== previousCommentId) closeCommentReport()
  },
  { flush: 'sync' },
)

onUnmounted(cancelPendingCommentReport)
</script>

<template>
  <div
    :id="`comment-${comment.commentId}`"
    :data-comment-id="comment.commentId"
    class="space-y-3 rounded-lg transition-colors sm:space-y-4"
    :class="{ 'nv-comment-item-new': isNewSinceLastView }"
  >
    <div class="flex space-x-2 sm:space-x-3">
      <div class="relative flex-shrink-0">
        <CornerDownRight
          v-if="depth > 0 && depth <= 2"
          class="absolute -left-5 top-1.5 h-3.5 w-3.5 nv-text-subtle sm:-left-6 sm:top-2 sm:h-4 sm:w-4"
        />

        <div
          v-if="canUseCommentActions && comment.author?.profileImageUrl"
          class="h-8 w-8 overflow-hidden rounded-full sm:h-10 sm:w-10"
        >
          <img
            :src="comment.author.profileImageUrl"
            :alt="comment.author.displayName"
            loading="lazy"
            decoding="async"
            class="h-full w-full object-cover"
          />
        </div>
        <div
          v-else
          class="flex h-8 w-8 items-center justify-center rounded-full nv-surface-muted sm:h-10 sm:w-10"
        >
          <UserIcon class="h-5 w-5 nv-text-subtle sm:h-6 sm:w-6" />
        </div>
      </div>

      <div class="min-w-0 flex-1 space-y-1">
        <div class="flex items-center justify-between gap-2 text-xs sm:text-sm">
          <div class="flex min-w-0 items-center gap-1">
            <UserMenu
              v-if="canUseCommentActions && comment.author"
              :user-id="comment.author.userId"
              :display-name="comment.author.displayName"
              size="inherit"
            />
            <span
              v-if="!comment.isDeleted && isAgentAuthor"
              class="inline-flex items-center rounded-full nv-status-info px-1.5 py-0.5 text-xs font-semibold"
            >
              {{ $t('comment.agentBadge') }}
            </span>
            <span
              v-else-if="!comment.isDeleted && comment.author?.representativeBadge"
              class="inline-flex items-center rounded-full border border-[var(--nv-line)] px-1.5 py-0.5 text-xs font-semibold"
            >
              {{ badgeName(comment.author.representativeBadge) }}
            </span>
            <span
              v-else-if="comment.isDeleted"
              class="text-xs font-medium nv-text-subtle sm:text-sm"
            >
              {{ $t('common.messages.unknown') }}
            </span>
            <span
              v-else-if="isBlockedAuthor"
              class="text-xs font-medium nv-text-subtle sm:text-sm"
            >
              {{ $t('comment.blockedAuthor') }}
            </span>
          </div>
          <p class="flex-shrink-0 text-xs nv-text-subtle sm:text-sm">
            <span class="sm:hidden">{{ createdAtShort }}</span>
            <span class="hidden sm:inline">{{ createdAtFull }}</span>
          </p>
        </div>

        <div v-if="isEditing" class="mt-2">
          <CommentForm
            :postId="postId"
            :commentId="comment.commentId"
            :initialContent="comment.content ?? ''"
            :initialMentions="comment.mentions ?? []"
            @success="handleEditSuccess"
            @cancel="isEditing = false"
          />
        </div>

        <p v-else-if="comment.isDeleted" class="text-xs italic nv-text-subtle sm:text-sm">
          {{ $t('comment.deleted') }}
        </p>
        <p v-else-if="isBlinded" class="text-xs italic text-[var(--nv-danger-text)] sm:text-sm">
          {{ $t('comment.blinded') }}
        </p>
        <p v-else-if="isBlockedAuthor" class="text-xs italic nv-text-subtle sm:text-sm">
          {{ $t('comment.blockedContent') }}
        </p>
        <template v-else>
          <p v-if="shouldShowReplyTarget" class="text-xs font-semibold text-[var(--nv-accent)]">
            @{{ replyTargetName }}
          </p>
          <SanitizedHtmlView
            v-if="isEmoticonOnly"
            tag="p"
            class="text-xs sm:text-sm"
            :html="renderedContent"
            @click="handleContentClick"
            @keydown="handleContentKeydown"
          />
          <SanitizedHtmlView
            v-else
            tag="p"
            class="text-xs nv-text-muted sm:text-sm"
            :html="renderedContent"
            @click="handleContentClick"
            @keydown="handleContentKeydown"
          />
        </template>

        <div class="mt-2 flex flex-wrap items-center gap-1 sm:gap-2">
          <button
            v-if="!props.readOnly && canUseCommentActions && !isBlinded"
            type="button"
            class="nv-touch-target nv-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
            :class="{ 'text-[var(--nv-danger-text)]': comment.liked }"
            :aria-label="comment.liked ? $t('comment.unlike') : $t('comment.like')"
            :aria-pressed="Boolean(comment.liked)"
            :disabled="isLikePending"
            @click="handleLike"
          >
            <Heart class="h-3.5 w-3.5" :fill="comment.liked ? 'currentColor' : 'none'" aria-hidden="true" />
            <span>{{ comment.likeCount }}</span>
          </button>

          <button
            v-if="canLoadReplies"
            type="button"
            class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
            @click="toggleReplies"
          >
            {{ replyToggleLabel }}
          </button>

          <button
            v-if="!props.readOnly && isAuthenticated && canUseCommentActions && !isBlinded"
            type="button"
            class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
            @click="isReplying = !isReplying"
          >
            {{ $t('comment.reply') }}
          </button>

          <button
            v-if="!props.readOnly && canReportComment"
            type="button"
            class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium text-[var(--nv-danger-text)] transition-colors hover:bg-[var(--nv-danger-bg)]"
            @click="openCommentReport"
          >
            {{ $t('common.report') }}
          </button>

          <template v-if="!props.readOnly && canUseCommentActions && isCommentAuthor && !isBlinded">
            <button
              v-if="!isEmoticonOnly"
              type="button"
              class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
              @click="isEditing = !isEditing"
            >
              {{ $t('common.edit') }}
            </button>
            <button
              type="button"
              class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium text-[var(--nv-danger-text)] transition-colors hover:bg-[var(--nv-danger-bg)]"
              @click="handleDelete"
            >
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <div
          v-if="isReplying"
          class="nv-comment-reply-branch mt-3 border-l-2 border-[var(--nv-line)] pl-2 sm:mt-4 sm:pl-4"
          :class="{ 'nv-comment-reply-branch-capped': depth >= 2 }"
        >
          <CommentForm
            :postId="postId"
            :parentId="comment.commentId"
            @success="handleReplySuccess"
            @cancel="isReplying = false"
          />
        </div>

        <div
          v-if="isRepliesOpen"
          class="nv-comment-reply-branch mt-3 border-l-2 border-[var(--nv-line)] pl-3 sm:mt-4 sm:pl-4"
          :class="{ 'nv-comment-reply-branch-capped': depth >= 2 }"
        >
          <p v-if="isRepliesLoading" class="text-xs nv-text-subtle">
            {{ $t('common.loading') }}
          </p>
          <div v-else-if="repliesError" class="text-xs nv-form-error" role="alert">
            <p>{{ $t('comment.loadRepliesFailed') }}</p>
            <button
              type="button"
              class="nv-focus-ring mt-2 min-h-11 rounded-md border nv-border px-3 text-xs font-medium"
              @click="refetchReplies()"
            >
              {{ $t('common.error.retry') }}
            </button>
          </div>
          <div v-else-if="replies.length > 0" class="space-y-3 sm:space-y-4">
            <CommentItem
              v-for="child in replies"
              :key="child.commentId"
              :comment="child"
              :postId="postId"
              :boardUrl="boardUrl"
              :depth="depth + 1"
              :reply-target-name="childReplyTargetName"
              :deep-link-comment-ids="deepLinkCommentIds"
              :read-only="props.readOnly"
              @reply-success="$emit('reply-success')"
              @edit-success="$emit('edit-success')"
              @delete="(childComment) => $emit('delete', childComment)"
            />
            <button
              v-if="replyHasNext"
              type="button"
              class="nv-touch-target rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
              @click="loadMoreReplies"
            >
              {{ $t('common.loadMore') }}
            </button>
          </div>
          <p v-else class="text-xs nv-text-subtle">
            {{ $t('common.noData') }}
          </p>
        </div>
      </div>
    </div>
    <ReportModal
      v-if="isReportModalOpen"
      :is-open="isReportModalOpen"
      :target-identity="`comment:${comment.commentId}`"
      :target-text="`${$t('common.comment')} #${comment.commentId}`"
      :submit="submitCommentReport"
      @close="closeCommentReport"
    />
  </div>
</template>

<style scoped>
.nv-comment-reply-branch-capped {
  border-left-color: transparent;
  padding-left: 0;
}

.nv-comment-item-new {
  background: color-mix(in srgb, var(--nv-accent) 7%, transparent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--nv-accent) 22%, transparent);
  padding: 0.75rem;
}

@media (min-width: 640px) {
  .nv-comment-reply-branch-capped {
    padding-left: 0;
  }
}

:deep(.comment-mention) {
  color: var(--nv-accent);
  cursor: pointer;
  font-weight: 600;
}

:deep(.comment-mention:focus-visible) {
  border-radius: 4px;
  outline: 2px solid var(--nv-accent);
  outline-offset: 2px;
}
</style>
