<script setup lang="ts">
import { ref, computed } from 'vue'
import { User as UserIcon, CornerDownRight } from 'lucide-vue-next'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import CommentForm from './CommentForm.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'
import { formatDate, formatDateShort } from '@/utils/date'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/utils/commentContent'

defineOptions({
  name: 'CommentItem'
})

const props = withDefaults(defineProps<{
  comment: Comment
  postId: number | string
  boardUrl: string
  depth?: number
}>(), {
  depth: 0
})

const emit = defineEmits<{
  (e: 'reply-success'): void
  (e: 'edit-success'): void
  (e: 'delete', comment: Comment): void
}>()

const { t } = useI18n()
const authStore = useAuthStore()

const isReplying = ref(false)
const isEditing = ref(false)

function handleReplySuccess() {
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

const renderedContent = computed(() => renderCommentContentHtml(props.comment.content))
const isEmoticonOnly = computed(() => isEmoticonOnlyContent(props.comment.content))
const isAgentAuthor = computed(() => props.comment.author?.authorType === 'AGENT')
</script>

<template>
  <div :id="`comment-${comment.commentId}`" class="space-y-3 sm:space-y-4">
    <div class="flex space-x-2 sm:space-x-3">
      <!-- Avatar -->
      <div class="flex-shrink-0 relative">
        <CornerDownRight v-if="depth > 0" class="absolute -left-5 sm:-left-6 top-1.5 sm:top-2 h-3.5 w-3.5 sm:h-4 sm:w-4 text-gray-300 dark:text-gray-600" />

        <div v-if="!comment.isDeleted && comment.author?.profileImageUrl"
          class="h-8 w-8 sm:h-10 sm:w-10 rounded-full overflow-hidden">
          <img :src="comment.author.profileImageUrl" :alt="comment.author.displayName"
            class="h-full w-full object-cover" />
        </div>
        <div v-else class="h-8 w-8 sm:h-10 sm:w-10 rounded-full bg-gray-200 flex items-center justify-center dark:bg-gray-700">
          <UserIcon class="h-5 w-5 sm:h-6 sm:w-6 text-gray-500 dark:text-gray-400" />
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 space-y-1 min-w-0">
        <div class="flex items-center justify-between gap-2 text-xs sm:text-sm">
          <div class="flex min-w-0 items-center gap-1">
            <UserMenu v-if="!comment.isDeleted" :user-id="comment.author.userId"
              :display-name="comment.author.displayName" size="inherit" />
            <span
              v-if="!comment.isDeleted && isAgentAuthor"
              class="inline-flex items-center rounded-full bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-700 dark:bg-sky-900/40 dark:text-sky-300"
            >
              에이전트
            </span>
            <span v-else-if="comment.isDeleted" class="text-xs sm:text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('common.messages.unknown')
            }}</span>
          </div>
          <p class="text-[11px] sm:text-sm text-gray-500 dark:text-gray-400 flex-shrink-0">
            <span class="sm:hidden">{{ formatDateShort(comment.createdAt) }}</span>
            <span class="hidden sm:inline">{{ formatDate(comment.createdAt) }}</span>
          </p>
        </div>

        <!-- Edit Form -->
        <div v-if="isEditing" class="mt-2">
          <CommentForm :postId="postId" :commentId="comment.commentId" :initialContent="comment.content"
            @success="handleEditSuccess" @cancel="isEditing = false" />
        </div>

        <!-- Comment Text -->
        <p v-else-if="comment.isDeleted" class="text-xs sm:text-sm text-gray-400 italic">
          {{ $t('comment.deleted') }}
        </p>
        <p v-else-if="isEmoticonOnly" v-html="renderedContent" class="text-xs sm:text-sm"></p>
        <p v-else v-html="renderedContent" class="text-xs sm:text-sm text-gray-700 dark:text-gray-300"></p>

        <!-- Actions -->
        <div v-if="!comment.isDeleted" class="mt-2 flex items-center gap-1 sm:space-x-2">
          <button v-if="authStore.isAuthenticated" @click="isReplying = !isReplying"
            class="px-2 py-1.5 text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
            {{ $t('comment.reply') }}
          </button>

          <template v-if="authStore.user?.userId === comment.author.userId">
            <button v-if="!isEmoticonOnly" @click="isEditing = !isEditing"
              class="px-2 py-1.5 text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
              {{ $t('common.edit') }}
            </button>
            <button @click="handleDelete"
              class="px-2 py-1.5 text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 font-medium rounded-md hover:bg-gray-100 dark:hover:bg-red-900/20 transition-colors">
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <!-- Reply Form -->
        <div v-if="isReplying" class="mt-3 sm:mt-4 pl-2 sm:pl-4 border-l-2 border-gray-200 dark:border-gray-700">
          <CommentForm :postId="postId" :parentId="comment.commentId" @success="handleReplySuccess"
            @cancel="isReplying = false" />
        </div>
      </div>
    </div>

    <!-- Recursive Children -->
    <div v-if="comment.children && comment.children.length > 0" class="pl-6 sm:pl-12 space-y-3 sm:space-y-4">
      <CommentItem v-for="child in comment.children" :key="child.commentId" :comment="child" :postId="postId"
        :boardUrl="boardUrl" :depth="depth + 1" @reply-success="$emit('reply-success')"
        @edit-success="$emit('edit-success')" @delete="(c) => $emit('delete', c)" />
    </div>
  </div>
</template>
