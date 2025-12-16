<script setup lang="ts">
import { ref, computed } from 'vue'
import { useComment } from '@/composables/useComment'
import { useAuthStore } from '@/stores/auth'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'
import type { Comment } from '@/api/comment'
import { useToastStore } from '@/stores/toast'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { useConfirm } from '@/composables/useConfirm'

const toastStore = useToastStore()
const { confirm } = useConfirm()

const props = defineProps<{
  postId: number | string
  boardUrl: string
}>()

const { t } = useI18n()
const authStore = useAuthStore()

const { useComments, useDeleteComment } = useComment()

// We might need to pass params for pagination if needed, currently just fetching all or default page
const params = ref({ page: 0, size: 50 })
const postId = computed(() => props.postId)

const { data: commentsData, isLoading, refetch } = useComments(postId, params)
const comments = computed<Comment[]>(() => commentsData.value?.content || [])

const { mutate: deleteComment } = useDeleteComment()

const replyToId = ref<number | null>(null)
const editingCommentId = ref<number | null>(null)

function handleReplySuccess() {
  replyToId.value = null
  // Query invalidation handled in composable
}

function handleEditSuccess() {
  editingCommentId.value = null
  // Query invalidation handled in composable
}

async function handleDelete(comment: Comment) {
  const isConfirmed = await confirm(t('common.messages.confirmDelete'))
  if (!isConfirmed) return

  deleteComment(comment.commentId, {
    onError: (err) => {
      logger.error('Failed to delete comment:', err)
      toastStore.addToast(t('comment.deleteFailed'), 'error')
    }
  })
}

function fetchComments() {
  refetch()
}
</script>

<template>
  <div class="mt-8">
    <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-6">{{ $t('comment.title') }}</h3>


    <!-- Comment List -->
    <div v-if="isLoading" class="text-center py-4">
      <BaseSpinner />
    </div>

    <div v-else class="space-y-6">
      <CommentItem v-for="comment in comments" :key="comment.commentId" :comment="comment" :postId="postId"
        :boardUrl="boardUrl" @reply-success="handleReplySuccess" @edit-success="handleEditSuccess"
        @delete="handleDelete" />

      <div v-if="comments.length === 0" class="text-center text-gray-500 dark:text-gray-400 py-4">
        {{ $t('comment.empty') }}
      </div>
    </div>

    <!-- New Comment Form -->
    <div v-if="authStore.isAuthenticated" class="mt-8 mb-8">
      <CommentForm :postId="postId" @success="fetchComments" />
    </div>
    <div v-else class="mt-8 mb-8 text-sm text-gray-500 dark:text-gray-400">
      <router-link to="/login"
        class="text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">{{
          $t('common.login') }}</router-link> {{ $t('comment.loginRequired', { login: '' }) }}
    </div>
  </div>
</template>

