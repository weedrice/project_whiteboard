<script setup>
import { ref, onMounted, watch } from 'vue'
import { commentApi } from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import CommentForm from './CommentForm.vue'
import { User, CornerDownRight } from 'lucide-vue-next'

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

const authStore = useAuthStore()
const comments = ref([])
const isLoading = ref(true)
const replyToId = ref(null)

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

async function handleDelete(commentId) {
  if (!confirm('정말 삭제하시겠습니까?')) return

  try {
    const { data } = await commentApi.deleteComment(commentId)
    if (data.success) {
      fetchComments()
    }
  } catch (err) {
    console.error('Failed to delete comment:', err)
    alert('Failed to delete comment.')
  }
}

onMounted(fetchComments)
watch(() => props.postId, fetchComments)
</script>

<template>
  <div class="mt-8">
    <h3 class="text-lg font-medium text-gray-900">댓글</h3>
    


    <!-- Comment List -->
    <div v-if="isLoading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else class="space-y-6">
      <div v-for="comment in comments" :key="comment.commentId">
        <!-- Parent Comment -->
        <div class="flex space-x-3">
          <div class="flex-shrink-0">
            <div class="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center">
              <User class="h-6 w-6 text-gray-500" />
            </div>
          </div>
          <div class="flex-1 space-y-1">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-medium text-gray-900">{{ comment.author.displayName }}</h3>
              <p class="text-sm text-gray-500">{{ formatDate(comment.createdAt) }}</p>
            </div>
            <p class="text-sm text-gray-700">{{ comment.content }}</p>
            
            <div class="mt-2 flex items-center space-x-2">
              <button 
                v-if="authStore.isAuthenticated"
                @click="replyToId = replyToId === comment.commentId ? null : comment.commentId"
                class="text-xs text-gray-500 hover:text-gray-900 font-medium"
              >
                답글
              </button>
              <button 
                v-if="authStore.user?.userId === comment.author.userId"
                @click="handleDelete(comment.commentId)"
                class="text-xs text-red-500 hover:text-red-700 font-medium ml-2"
              >
                삭제
              </button>
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
          <div v-for="child in comment.children" :key="child.commentId" class="flex space-x-3">
            <div class="flex-shrink-0 relative">
              <CornerDownRight class="absolute -left-6 top-2 h-4 w-4 text-gray-300" />
              <div class="h-8 w-8 rounded-full bg-gray-100 flex items-center justify-center">
                <User class="h-5 w-5 text-gray-400" />
              </div>
            </div>
            <div class="flex-1 space-y-1">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-medium text-gray-900">{{ child.author.displayName }}</h3>
                <p class="text-sm text-gray-500">{{ formatDate(child.createdAt) }}</p>
              </div>
              <p class="text-sm text-gray-700">{{ child.content }}</p>
              <div class="mt-1">
                 <button 
                  v-if="authStore.user?.userId === child.author.userId"
                  @click="handleDelete(child.commentId)"
                  class="text-xs text-red-500 hover:text-red-700 font-medium"
                >
                  삭제
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="comments.length === 0" class="text-center text-gray-500 py-4">
        아직 댓글이 없습니다. 첫 번째 댓글을 남겨보세요!
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
      <router-link to="/login" class="text-indigo-600 hover:text-indigo-500">로그인</router-link> 후 댓글을 작성할 수 있습니다.
    </div>
  </div>
</template>
