<script setup>
import { ref, onMounted, watch } from 'vue'
import { commentApi } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import CommentForm from './CommentForm.vue'
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
    <h3 class="text-lg font-medium text-gray-900">{{ $t('comment.title') }}</h3>
    

    <!-- Comment List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else class="space-y-6">
      <div v-for="comment in comments" :key="comment.commentId" :id="`comment-${comment.commentId}`" class="space-y-4">
        <!-- Parent Comment -->
        <div class="flex space-x-3">
          <div class="flex-shrink-0">
            <div class="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center">
              <User class="h-6 w-6 text-gray-500" />
            </div>
          </div>
          <div class="flex-1 space-y-1">
            <div class="flex items-center justify-between">
              <UserMenu
                v-if="!comment.isDeleted"
                :user-id="comment.author.userId"
                :display-name="comment.author.displayName"
              />
              <span v-else class="text-sm font-medium text-gray-500">{{ $t('common.unknown') }}</span>
              <p class="text-sm text-gray-500">{{ formatDate(comment.createdAt) }}</p>
            </div>
            
            <!-- Edit Form -->
            <div v-if="editingCommentId === comment.commentId" class="mt-2">
                <CommentForm
                    :postId="postId"
                    :commentId="comment.commentId"
                    :initialContent="comment.content"
                    @success="handleEditSuccess"
                    @cancel="editingCommentId = null"
                />
            </div>
            <!-- Content -->
            <p v-else class="text-sm text-gray-700" :class="{ 'text-gray-400 italic': comment.isDeleted }">
                {{ comment.isDeleted ? $t('comment.deleted') : comment.content }}
            </p>
            
            <div v-if="!comment.isDeleted" class="mt-2 flex items-center space-x-2">
              <button 
                v-if="authStore.isAuthenticated"
                @click="replyToId = replyToId === comment.commentId ? null : comment.commentId"
                class="text-xs text-gray-500 hover:text-gray-900 font-medium"
              >
                {{ $t('comment.reply') }}
              </button>
              <template v-if="authStore.user?.userId === comment.author.userId">
                  <button 
                    @click="startEdit(comment)"
                    class="text-xs text-gray-500 hover:text-gray-900 font-medium ml-2"
                  >
                    {{ $t('common.edit') }}
                  </button>
                  <button 
                    @click="handleDelete(comment)"
                    class="text-xs text-red-500 hover:text-red-700 font-medium ml-2"
                  >
                    {{ $t('common.delete') }}
                  </button>
              </template>
            </div>

            <!-- Reply Form -->
            <div v-if="replyToId === comment.commentId" class="mt-4 pl-4 border-l-2 border-gray-200">
              <CommentForm 
                :postId="postId" 
                :parentId="comment.commentId" 
                @success="handleReplySuccess" 
                @cancel="replyToId = null"
              />
            </div>
          </div>
        </div>

        <!-- Child Comments (Depth 1) -->
        <div v-if="comment.children && comment.children.length > 0" class="mt-4 pl-12 space-y-4">
          <div v-for="child in comment.children" :key="child.commentId" :id="`comment-${child.commentId}`" class="flex space-x-3">
            <div class="flex-shrink-0 relative">
              <CornerDownRight class="absolute -left-6 top-2 h-4 w-4 text-gray-300" />
              <div class="h-8 w-8 rounded-full bg-gray-100 flex items-center justify-center">
                <User class="h-5 w-5 text-gray-400" />
              </div>
            </div>
            <div class="flex-1 space-y-1">
              <div class="flex items-center justify-between">
                <UserMenu
                  v-if="!child.isDeleted"
                  :user-id="child.author.userId"
                  :display-name="child.author.displayName"
                />
                <span v-else class="text-sm font-medium text-gray-500">{{ $t('common.unknown') }}</span>
                <p class="text-sm text-gray-500">{{ formatDate(child.createdAt) }}</p>
              </div>
              
              <!-- Edit Form (Child) -->
              <div v-if="editingCommentId === child.commentId" class="mt-2">
                <CommentForm
                    :postId="postId"
                    :commentId="child.commentId"
                    :initialContent="child.content"
                    @success="handleEditSuccess"
                    @cancel="editingCommentId = null"
                />
              </div>
              <!-- Content (Child) -->
              <p v-else class="text-sm text-gray-700" :class="{ 'text-gray-400 italic': child.isDeleted }">
                {{ child.isDeleted ? $t('comment.deleted') : child.content }}
              </p>

              <div v-if="!child.isDeleted" class="mt-1">
                 <template v-if="authStore.user?.userId === child.author.userId">
                    <button 
                        @click="startEdit(child)"
                        class="text-xs text-gray-500 hover:text-gray-900 font-medium mr-2"
                    >
                        {{ $t('common.edit') }}
                    </button>
                    <button 
                        @click="handleDelete(child)"
                        class="text-xs text-red-500 hover:text-red-700 font-medium"
                    >
                        {{ $t('common.delete') }}
                    </button>
                 </template>
              </div>
            </div>
          </div>
        </div>
      </div>
      
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
