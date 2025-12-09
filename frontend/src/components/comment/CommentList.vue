<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useComment } from '@/composables/useComment'
import { useAuthStore } from '@/stores/auth'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'
import { User, CornerDownRight } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'
import logger from '@/utils/logger'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  postId: {
    type: [Number, String],
    required: true
  },
  boardUrl: {
    type: String,
    required: true
  }
})

const { t } = useI18n()
const authStore = useAuthStore()

const { useComments, useDeleteComment } = useComment()

// We might need to pass params for pagination if needed, currently just fetching all or default page
const params = ref({ page: 0, size: 50 }) 
const postId = computed(() => props.postId)

const { data: commentsData, isLoading, refetch } = useComments(postId, params)
const comments = computed(() => commentsData.value?.content || [])

const { mutate: deleteComment } = useDeleteComment()

const replyToId = ref(null)
const editingCommentId = ref(null)

function handleReplySuccess() {
  replyToId.value = null
  // Query invalidation handled in composable
}

function handleEditSuccess() {
  editingCommentId.value = null
  // Query invalidation handled in composable
}

function handleDelete(comment) {
  if (!confirm(t('common.messages.confirmDelete'))) return

  deleteComment(comment.commentId, {
      onError: (err) => {
          logger.error('Failed to delete comment:', err)
          alert(t('comment.deleteFailed'))
      }
  })
}
</script>

<template>
  <div class="mt-8">
    <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-6">{{ $t('comment.title') }}</h3>
    

    <!-- Comment List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else class="space-y-6">
      <CommentItem
        v-for="comment in comments"
        :key="comment.commentId"
        :comment="comment"
        :postId="postId"
        :boardUrl="boardUrl"
        @reply-success="handleReplySuccess"
        @edit-success="handleEditSuccess"
        @delete="handleDelete"
      />
      
      <div v-if="comments.length === 0" class="text-center text-gray-500 dark:text-gray-400 py-4">
        {{ $t('comment.empty') }}
      </div>
    </div>

    <!-- New Comment Form -->
    <div v-if="authStore.isAuthenticated" class="mt-8 mb-8">
      <CommentForm 
        :postId="postId" 
        @success="fetchComments" 
      />
    </div>
    <div v-else class="mt-8 mb-8 text-sm text-gray-500 dark:text-gray-400">
      <router-link to="/login" class="text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">{{ $t('common.login') }}</router-link> {{ $t('comment.loginRequired', { login: '' }) }}
    </div>
  </div>
</template>
