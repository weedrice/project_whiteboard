<script setup lang="ts">
import { ref, computed } from 'vue'
import { User as UserIcon, CornerDownRight } from 'lucide-vue-next'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import CommentForm from './CommentForm.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import type { Comment } from '@/api/comment'

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

import { formatDate } from '@/utils/date'

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

// 이모티콘 마크다운을 이미지로 변환
const renderedContent = computed(() => {
  if (!props.comment.content) return ''

  // ![emoticon](URL) 패턴을 img 태그로 변환
  const emoticonPattern = /!\[emoticon\]\(([^)]+)\)/g
  return props.comment.content.replace(emoticonPattern, '<img src="$1" class="comment-emoticon" alt="emoticon" />')
})

// 순수 이모티콘 댓글인지 확인 (이모티콘만 포함된 댓글)
const isEmoticonOnly = computed(() => {
  if (!props.comment.content) return false
  const emoticonPattern = /^!\[emoticon\]\([^)]+\)$/
  return emoticonPattern.test(props.comment.content.trim())
})
</script>

<template>
  <div :id="`comment-${comment.commentId}`" class="space-y-4">
    <div class="flex space-x-3">
      <!-- Avatar -->
      <div class="flex-shrink-0 relative">
        <CornerDownRight v-if="depth > 0" class="absolute -left-6 top-2 h-4 w-4 text-gray-300 dark:text-gray-600" />

        <div v-if="!comment.isDeleted && comment.author?.profileImageUrl"
          class="h-10 w-10 rounded-full overflow-hidden">
          <img :src="comment.author.profileImageUrl" :alt="comment.author.displayName"
            class="h-full w-full object-cover" />
        </div>
        <div v-else class="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center dark:bg-gray-700">
          <UserIcon class="h-6 w-6 text-gray-500 dark:text-gray-400" />
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 space-y-1">
        <div class="flex items-center justify-between">
          <UserMenu v-if="!comment.isDeleted" :user-id="comment.author.userId"
            :display-name="comment.author.displayName" />
          <span v-else class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('common.messages.unknown')
          }}</span>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ formatDate(comment.createdAt) }}</p>
        </div>

        <!-- Edit Form -->
        <div v-if="isEditing" class="mt-2">
          <CommentForm :postId="postId" :commentId="comment.commentId" :initialContent="comment.content"
            @success="handleEditSuccess" @cancel="isEditing = false" />
        </div>

        <!-- Comment Text -->
        <p v-else-if="comment.isDeleted" class="text-sm text-gray-400 italic">
          {{ $t('comment.deleted') }}
        </p>
        <p v-else-if="isEmoticonOnly" v-html="renderedContent" class="text-sm"></p>
        <p v-else v-html="renderedContent" class="text-sm text-gray-700 dark:text-gray-300"></p>

        <!-- Actions -->
        <div v-if="!comment.isDeleted" class="mt-2 flex items-center space-x-2">
          <button v-if="authStore.isAuthenticated" @click="isReplying = !isReplying"
            class="text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium">
            {{ $t('comment.reply') }}
          </button>

          <template v-if="authStore.user?.userId === comment.author.userId">
            <button v-if="!isEmoticonOnly" @click="isEditing = !isEditing"
              class="text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium ml-2">
              {{ $t('common.edit') }}
            </button>
            <button @click="handleDelete"
              class="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 font-medium ml-2">
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <!-- Reply Form -->
        <div v-if="isReplying" class="mt-4 pl-4 border-l-2 border-gray-200 dark:border-gray-700">
          <CommentForm :postId="postId" :parentId="comment.commentId" @success="handleReplySuccess"
            @cancel="isReplying = false" />
        </div>
      </div>
    </div>

    <!-- Recursive Children -->
    <div v-if="comment.children && comment.children.length > 0" class="pl-12 space-y-4">
      <CommentItem v-for="child in comment.children" :key="child.commentId" :comment="child" :postId="postId"
        :boardUrl="boardUrl" :depth="depth + 1" @reply-success="$emit('reply-success')"
        @edit-success="$emit('edit-success')" @delete="(c) => $emit('delete', c)" />
    </div>
  </div>
</template>
