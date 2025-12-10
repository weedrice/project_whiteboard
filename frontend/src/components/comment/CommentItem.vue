<script setup lang="ts">
import { ref } from 'vue'
import { User as UserIcon, CornerDownRight } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'
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

const isReplying = ref(false)
const isEditing = ref(false)

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleString()
}

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
</script>

<template>
  <div :id="`comment-${comment.commentId}`" class="space-y-4">
    <div class="flex space-x-3">
      <!-- Avatar -->
      <div class="flex-shrink-0 relative">
        <CornerDownRight v-if="depth > 0" class="absolute -left-6 top-2 h-4 w-4 text-gray-300 dark:text-gray-600" />
        <div class="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center dark:bg-gray-700">
          <UserIcon class="h-6 w-6 text-gray-500 dark:text-gray-400" />
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 space-y-1">
        <div class="flex items-center justify-between">
          <UserMenu
            v-if="!comment.isDeleted"
            :user-id="comment.author.userId"
            :display-name="comment.author.displayName"
          />
          <span v-else class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('common.messages.unknown') }}</span>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ formatDate(comment.createdAt) }}</p>
        </div>

        <!-- Edit Form -->
        <div v-if="isEditing" class="mt-2">
          <CommentForm
            :postId="postId"
            :commentId="comment.commentId"
            :initialContent="comment.content"
            @success="handleEditSuccess"
            @cancel="isEditing = false"
          />
        </div>
        
        <!-- Comment Text -->
        <p v-else class="text-sm text-gray-700 dark:text-gray-300" :class="{ 'text-gray-400 italic': comment.isDeleted }">
          {{ comment.isDeleted ? $t('comment.deleted') : comment.content }}
        </p>

        <!-- Actions -->
        <div v-if="!comment.isDeleted" class="mt-2 flex items-center space-x-2">
          <button 
            v-if="authStore.isAuthenticated"
            @click="isReplying = !isReplying"
            class="text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium"
          >
            {{ $t('comment.reply') }}
          </button>
          
          <template v-if="authStore.user?.id === comment.author.userId">
            <button 
              @click="isEditing = !isEditing"
              class="text-xs text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200 font-medium ml-2"
            >
              {{ $t('common.edit') }}
            </button>
            <button 
              @click="handleDelete"
              class="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 font-medium ml-2"
            >
              {{ $t('common.delete') }}
            </button>
          </template>
        </div>

        <!-- Reply Form -->
        <div v-if="isReplying" class="mt-4 pl-4 border-l-2 border-gray-200 dark:border-gray-700">
          <CommentForm 
            :postId="postId" 
            :parentId="comment.commentId" 
            @success="handleReplySuccess" 
            @cancel="isReplying = false"
          />
        </div>
      </div>
    </div>

    <!-- Recursive Children -->
    <div v-if="comment.children && comment.children.length > 0" class="pl-12 space-y-4">
      <CommentItem
        v-for="child in comment.children"
        :key="child.commentId"
        :comment="child"
        :postId="postId"
        :boardUrl="boardUrl"
        :depth="depth + 1"
        @reply-success="$emit('reply-success')"
        @edit-success="$emit('edit-success')"
        @delete="(c) => $emit('delete', c)"
      />
    </div>
  </div>
</template>
