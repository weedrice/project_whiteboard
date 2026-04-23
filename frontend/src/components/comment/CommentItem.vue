<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { User as UserIcon, CornerDownRight } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { useComment } from '@/composables/useComment'
import { useAuthStore } from '@/stores/auth'
import { formatDate } from '@/utils/date'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/utils/commentContent'
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
}>(), {
  depth: 0,
})

const emit = defineEmits<{
  (e: 'reply-success'): void
  (e: 'edit-success'): void
  (e: 'delete', comment: Comment): void
}>()

const { t } = useI18n()
const authStore = useAuthStore() as ReturnType<typeof useAuthStore> | undefined
const { useReplies } = useComment()

const isReplying = ref(false)
const isEditing = ref(false)
const isRepliesOpen = ref(!props.comment.isDeleted && Boolean(props.comment.hasReplies))
const optimisticHasReplies = ref(false)
const replyParams = ref({ page: 0, size: 50 })
const loadedReplies = ref<Comment[]>([])
const replyHasNext = ref(false)
const commentId = computed(() => props.comment.commentId)
const canLoadReplies = computed(() => !props.comment.isDeleted && Boolean(props.comment.hasReplies || optimisticHasReplies.value))
const repliesEnabled = computed(() => isRepliesOpen.value && canLoadReplies.value)

const { data: repliesData, isLoading: isRepliesLoading, error: repliesError } =
  useReplies(commentId, replyParams, repliesEnabled)

const replies = computed(() => loadedReplies.value)
const renderedContent = computed(() => renderCommentContentHtml(props.comment.content))
const isEmoticonOnly = computed(() => isEmoticonOnlyContent(props.comment.content))
const isAgentAuthor = computed(() => props.comment.author?.authorType === 'AGENT')
const isAuthenticated = computed(() => Boolean(authStore?.isAuthenticated))
const currentUserId = computed(() => authStore?.user?.userId)
const isCommentAuthor = computed(() => currentUserId.value === props.comment.author?.userId)
const createdAtShort = computed(() => formatCommentDateShort(props.comment.createdAt))
const createdAtFull = computed(() => formatDate(props.comment.createdAt))
const replyToggleLabel = computed(() => {
  if (isRepliesOpen.value) {
    return t('comment.hideReplies')
  }

  return t('comment.viewReplies', { count: props.comment.replyCount })
})

function handleReplySuccess() {
  optimisticHasReplies.value = true
  isReplying.value = false
  isRepliesOpen.value = true
  replyParams.value = { ...replyParams.value, page: 0 }
  emit('reply-success')
}

function handleEditSuccess() {
  isEditing.value = false
  emit('edit-success')
}

function handleDelete() {
  emit('delete', props.comment)
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

function formatCommentDateShort(dateString: string): string {
  if (!dateString) {
    return ''
  }

  const date = new Date(dateString)
  const yy = String(date.getFullYear()).slice(-2)
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')

  return `${yy}.${mm}.${dd} ${hh}:${mi}`
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

  if (!props.comment.hasReplies && !optimisticHasReplies.value && loadedReplies.value.length === 0) {
    optimisticHasReplies.value = false
    isRepliesOpen.value = false
  }
}, { immediate: true })

watch(() => props.comment.hasReplies, (hasReplies) => {
  if (!hasReplies && !optimisticHasReplies.value) {
    optimisticHasReplies.value = false
    loadedReplies.value = []
    replyHasNext.value = false
    isRepliesOpen.value = false
    return
  }

  if (hasReplies && !props.comment.isDeleted) {
    isRepliesOpen.value = true
  }
})

watch(() => props.comment.isDeleted, (isDeleted) => {
  if (!isDeleted) {
    return
  }

  optimisticHasReplies.value = false
  loadedReplies.value = []
  replyHasNext.value = false
  isRepliesOpen.value = false
})
</script>

<template>
  <div :id="`comment-${comment.commentId}`" class="space-y-3 sm:space-y-4">
    <div class="flex space-x-2 sm:space-x-3">
      <div class="relative flex-shrink-0">
        <CornerDownRight
          v-if="depth > 0"
          class="absolute -left-5 top-1.5 h-3.5 w-3.5 text-gray-300 sm:-left-6 sm:top-2 sm:h-4 sm:w-4 dark:text-gray-600"
        />

        <div
          v-if="!comment.isDeleted && comment.author?.profileImageUrl"
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
          class="flex h-8 w-8 items-center justify-center rounded-full bg-gray-200 dark:bg-gray-700 sm:h-10 sm:w-10"
        >
          <UserIcon class="h-5 w-5 text-gray-500 dark:text-gray-400 sm:h-6 sm:w-6" />
        </div>
      </div>

      <div class="min-w-0 flex-1 space-y-1">
        <div class="flex items-center justify-between gap-2 text-xs sm:text-sm">
          <div class="flex min-w-0 items-center gap-1">
            <UserMenu
              v-if="!comment.isDeleted && comment.author"
              :user-id="comment.author.userId"
              :display-name="comment.author.displayName"
              size="inherit"
            />
            <span
              v-if="!comment.isDeleted && isAgentAuthor"
              class="inline-flex items-center rounded-full bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
            >
              {{ $t('comment.agentBadge') }}
            </span>
            <span
              v-else-if="comment.isDeleted"
              class="text-xs font-medium text-gray-500 dark:text-gray-400 sm:text-sm"
            >
              {{ $t('common.messages.unknown') }}
            </span>
          </div>
          <p class="flex-shrink-0 text-[11px] text-gray-500 dark:text-gray-400 sm:text-sm">
            <span class="sm:hidden">{{ createdAtShort }}</span>
            <span class="hidden sm:inline">{{ createdAtFull }}</span>
          </p>
        </div>

        <div v-if="isEditing" class="mt-2">
          <CommentForm
            :postId="postId"
            :commentId="comment.commentId"
            :initialContent="comment.content"
            @success="handleEditSuccess"
            @cancel="isEditing = false"
          />
        </div>

        <p v-else-if="comment.isDeleted" class="text-xs italic text-gray-400 sm:text-sm">
          {{ $t('comment.deleted') }}
        </p>
        <p v-else-if="isEmoticonOnly" class="text-xs sm:text-sm" v-html="renderedContent"></p>
        <p v-else class="text-xs text-gray-700 dark:text-gray-300 sm:text-sm" v-html="renderedContent"></p>

        <div class="mt-2 flex flex-wrap items-center gap-1 sm:gap-2">
          <button
            v-if="canLoadReplies"
            type="button"
            class="rounded-md px-2 py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
            @click="toggleReplies"
          >
            {{ replyToggleLabel }}
          </button>

          <button
            v-if="isAuthenticated && !comment.isDeleted"
            type="button"
            class="rounded-md px-2 py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
            @click="isReplying = !isReplying"
          >
            {{ $t('comment.reply') }}
          </button>

          <template v-if="!comment.isDeleted && isCommentAuthor">
            <button
              v-if="!isEmoticonOnly"
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
              @click="isEditing = !isEditing"
            >
              {{ $t('common.edit') }}
            </button>
            <button
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium text-red-500 transition-colors hover:bg-gray-100 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-900/20 dark:hover:text-red-300"
              @click="handleDelete"
            >
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <div
          v-if="isReplying"
          class="mt-3 border-l-2 border-gray-200 pl-2 dark:border-gray-700 sm:mt-4 sm:pl-4"
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
          class="mt-3 border-l-2 border-gray-100 pl-3 dark:border-gray-800 sm:mt-4 sm:pl-4"
        >
          <p v-if="isRepliesLoading" class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('common.loading') }}
          </p>
          <p v-else-if="repliesError" class="text-xs text-red-500 dark:text-red-300">
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
              @reply-success="$emit('reply-success')"
              @edit-success="$emit('edit-success')"
              @delete="(childComment) => $emit('delete', childComment)"
            />
            <button
              v-if="replyHasNext"
              type="button"
              class="rounded-md px-2 py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
              @click="loadMoreReplies"
            >
              {{ $t('common.loadMore') }}
            </button>
          </div>
          <p v-else class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('common.noData') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
