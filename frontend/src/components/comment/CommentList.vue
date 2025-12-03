<script setup>
import { ref, onMounted, watch } from 'vue'
import { commentApi } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'
import { User, CornerDownRight } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'

import { useI18n } from 'vue-i18n'

const props = defineProps({
  postId: {
    type: [Number, String],
    required: true
  },
  boardUrl: { // 추가: boardUrl
    type: String,
    required: true
  }
})

const { t } = useI18n()

const authStore = useAuthStore()
const comments = ref([])
const isLoading = ref(true)
const replyToId = ref(null)
const editingCommentId = ref(null)

async function fetchComments() {
  isLoading.value = true
  try {
    const { data } = await commentApi.getComments(props.postId)
    if (data.success) {
      comments.value = data.data.content
    }
  } catch (err) {
    console.error('Failed to load comments:', err)
  } finally {
    isLoading.value = false
  }
}

function formatDate(dateString) {
  return new Date(dateString).toLocaleString()
}

function handleReplySuccess() {
  replyToId.value = null
  fetchComments()
}

function startEdit(comment) {
    editingCommentId.value = comment.commentId
}

function handleEditSuccess() {
    editingCommentId.value = null
    fetchComments()
}

async function handleDelete(comment) {
  if (!confirm(t('common.confirmDelete'))) return

  // Soft delete check: If it has children, just mark as deleted locally (if backend doesn't handle it)
  // Actually, we should call delete API. If backend deletes it physically, it's gone.
  // If backend supports soft delete, it will return success.
  // We will assume backend deletes it. If we want to show "Deleted", we rely on backend response or manual update.
  // Strategy: Call delete. If success, fetch comments. 
  // If the comment had children, we hope the backend kept it as "deleted". 
  // If not, we can't do much unless we fake it.
  // Let's try standard delete first. If the user wants "Deleted" message, 
  // it implies the comment structure should remain.
  
  try {
    const { data } = await commentApi.deleteComment(comment.commentId)
    if (data.success) {
      fetchComments()
    }
  } catch (err) {
    console.error('Failed to delete comment:', err)
    alert(t('comment.deleteFailed'))
  }
}

onMounted(fetchComments)
watch(() => props.postId, fetchComments)
</script>

<template>
  <div class="mt-8">
    <h3 class="text-lg font-medium text-gray-900 mb-6">{{ $t('comment.title') }}</h3>
    

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
      
      <div v-if="comments.length === 0" class="text-center text-gray-500 py-4">
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
    <div v-else class="mt-8 mb-8 text-sm text-gray-500">
      <router-link to="/login" class="text-indigo-600 hover:text-indigo-500">{{ $t('auth.login') }}</router-link> {{ $t('comment.loginRequired', { login: '' }) }}
    </div>
  </div>
</template>
