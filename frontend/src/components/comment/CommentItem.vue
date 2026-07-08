<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { User as UserIcon, CornerDownRight } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { useCommentReplies } from '@/composables/useCommentReplies'
import { useCommentAuthorState } from '@/composables/useCommentAuthorState'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatDateShort } from '@/utils/date'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/features/comments/commentContent'
import type { SanitizedHtml } from '@/utils/sanitize'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import CommentForm from './CommentForm.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'

defineOptions({
  name: 'CommentItem',
})

const props = withDefaults(defineProps<{
  comment: Comment
  postId: number | string
  boardUrl: string
  depth?: number
  replyTargetName?: string
}>(), {
  depth: 0,
  replyTargetName: '',
})

const emit = defineEmits<{
  (e: 'reply-success'): void
  (e: 'edit-success'): void
  (e: 'delete', comment: Comment): void
}>()

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore() as ReturnType<typeof useAuthStore> | undefined

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
} = useCommentReplies(commentRef)
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
  props.comment.mentions ?? []
))
const childReplyTargetName = computed(() => (
  props.comment.author?.displayName?.trim() || t('user.deletedUser')
))
const shouldShowReplyTarget = computed(() => props.depth >= 3 && props.replyTargetName.trim().length > 0)
const isEmoticonOnly = computed(() => isEmoticonOnlyContent(props.comment.content ?? ''))
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
</script>

<template>
  <div :id="`comment-${comment.commentId}`" class="space-y-3 sm:space-y-4">
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
              class="inline-flex items-center rounded-full nv-status-info px-1.5 py-0.5 text-[10px] font-semibold"
            >
              {{ $t('comment.agentBadge') }}
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
          <p class="flex-shrink-0 text-[11px] nv-text-subtle sm:text-sm">
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
        <p v-else-if="isBlockedAuthor" class="text-xs italic nv-text-subtle sm:text-sm">
          {{ $t('comment.blockedContent') }}
        </p>
        <template v-else>
          <p v-if="shouldShowReplyTarget" class="text-[11px] font-semibold text-[var(--nv-accent)] sm:text-xs">
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
            v-if="canLoadReplies"
            type="button"
            class="rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
            @click="toggleReplies"
          >
            {{ replyToggleLabel }}
          </button>

          <button
            v-if="isAuthenticated && canUseCommentActions"
            type="button"
            class="rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
            @click="isReplying = !isReplying"
          >
            {{ $t('comment.reply') }}
          </button>

          <template v-if="canUseCommentActions && isCommentAuthor">
            <button
              v-if="!isEmoticonOnly"
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
              @click="isEditing = !isEditing"
            >
              {{ $t('common.edit') }}
            </button>
            <button
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium text-[var(--nv-danger-text)] transition-colors hover:bg-[var(--nv-danger-bg)]"
              @click="handleDelete"
            >
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <div
          v-if="isReplying"
          class="nv-comment-reply-branch mt-3 border-l-2 border-[var(--nv-border)] pl-2 sm:mt-4 sm:pl-4"
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
          class="nv-comment-reply-branch mt-3 border-l-2 border-[var(--nv-border)] pl-3 sm:mt-4 sm:pl-4"
          :class="{ 'nv-comment-reply-branch-capped': depth >= 2 }"
        >
          <p v-if="isRepliesLoading" class="text-xs nv-text-subtle">
            {{ $t('common.loading') }}
          </p>
          <p v-else-if="repliesError" class="text-xs nv-form-error">
            {{ $t('comment.loadRepliesFailed') }}
          </p>
          <div v-else-if="replies.length > 0" class="space-y-3 sm:space-y-4">
            <CommentItem
              v-for="child in replies"
              :key="child.commentId"
              :comment="child"
              :postId="postId"
              :boardUrl="boardUrl"
              :depth="depth + 1"
              :reply-target-name="childReplyTargetName"
              @reply-success="$emit('reply-success')"
              @edit-success="$emit('edit-success')"
              @delete="(childComment) => $emit('delete', childComment)"
            />
            <button
              v-if="replyHasNext"
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium nv-text-subtle nv-hover-surface"
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
  </div>
</template>

<style scoped>
.nv-comment-reply-branch-capped {
  border-left-color: transparent;
  padding-left: 0;
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
