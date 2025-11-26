<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { useAuthStore } from '@/stores/auth'
import { User, Clock, ThumbsUp, MessageSquare, Eye, ArrowLeft, MoreHorizontal } from 'lucide-vue-next'
import CommentList from '@/components/comment/CommentList.vue'
import PostTags from '@/components/tag/PostTags.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const post = ref(null)
const isLoading = ref(true)
const error = ref('')

const isAuthor = computed(() => {
  return authStore.user && post.value && authStore.user.userId === post.value.author.userId
})

function formatDate(dateString) {
  return new Date(dateString).toLocaleString()
}

async function fetchPost() {
  isLoading.value = true
  error.value = ''
  try {
    const { data } = await postApi.getPost(route.params.postId)
    if (data.success) {
      post.value = data.data
      // Ensure numeric counts
      post.value.likeCount = post.value.likeCount || 0
      post.value.commentCount = post.value.commentCount || 0
      post.value.viewCount = post.value.viewCount || 0
    }
  } catch (err) {
    console.error('Failed to load post:', err)
    error.value = 'Failed to load post details.'
  } finally {
    isLoading.value = false
  }
}

async function handleDelete() {
  if (!confirm('Are you sure you want to delete this post?')) return

  try {
    const { data } = await postApi.deletePost(route.params.postId)
    if (data.success) {
      router.push(`/board/${post.value.board.boardId}`)
    }
  } catch (err) {
    console.error('Failed to delete post:', err)
    alert('Failed to delete post.')
  }
}

async function handleLike() {
  if (!authStore.isAuthenticated) {
    alert('Please login to like this post.')
    return
  }

  try {
    if (post.value.isLiked) {
      const { data } = await postApi.unlikePost(route.params.postId)
      if (data.success) {
        post.value.isLiked = false
        post.value.likeCount = data.data.likeCount
      }
    } else {
      const { data } = await postApi.likePost(route.params.postId)
      if (data.success) {
        post.value.isLiked = true
        post.value.likeCount = data.data.likeCount
      }
    }
  } catch (err) {
    console.error('Failed to toggle like:', err)
  }
}

onMounted(fetchPost)
</script>

<template>
  <div class="max-w-4xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
      <div class="mt-4">
        <button @click="router.back()" class="text-indigo-600 hover:text-indigo-500">
          Go Back
        </button>
      </div>
    </div>

    <div v-else-if="post" class="bg-white shadow overflow-hidden sm:rounded-lg">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200">
        <div class="flex items-center justify-between">
          <button 
            @click="router.push(`/board/${post.board.boardId}`)" 
            class="inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft class="h-4 w-4 mr-1" />
            Back to {{ post.board.boardName }}
          </button>
          
          <div v-if="isAuthor" class="relative">
             <router-link 
              :to="`/board/${post.board.boardId}/post/${post.postId}/edit`"
              class="mr-2 inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              Edit
            </router-link>
            <button 
              @click="handleDelete"
              class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm leading-4 font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
            >
              Delete
            </button>
          </div>
        </div>
        
        <h1 class="mt-4 text-3xl font-bold text-gray-900">
          {{ post.title }}
        </h1>
        
        <div class="mt-2 flex items-center text-sm text-gray-500">
          <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800 mr-3">
            {{ post.category?.name || 'General' }}
          </span>
          <span v-if="post.isNsfw" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 mr-3">
            NSFW
          </span>
          <span v-if="post.isSpoiler" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800 mr-3">
            Spoiler
          </span>
          <div class="flex items-center mr-4">
            <User class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
            {{ post.author.displayName }}
          </div>
          <div class="flex items-center">
            <Clock class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
            {{ formatDate(post.createdAt) }}
          </div>
        </div>
      </div>

      <!-- Content -->
      <div class="px-4 py-5 sm:p-6 min-h-[200px] prose max-w-none">
        <div class="whitespace-pre-wrap">{{ post.contents }}</div>
      </div>

      <!-- Tags -->
      <div v-if="post.tags && post.tags.length > 0" class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-gray-50">
        <PostTags :modelValue="post.tags" :readOnly="true" />
      </div>

      <!-- Stats & Actions -->
      <div class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-gray-50 flex items-center justify-between">
        <div class="flex space-x-6 text-sm text-gray-500">
          <div class="flex items-center">
            <ThumbsUp class="mr-1.5 h-4 w-4" />
            {{ post.likeCount }} Likes
          </div>
          <div class="flex items-center">
            <MessageSquare class="mr-1.5 h-4 w-4" />
            {{ post.commentCount }} Comments
          </div>
          <div class="flex items-center">
            <Eye class="mr-1.5 h-4 w-4" />
            {{ post.viewCount }} Views
          </div>
        </div>
        
        <!-- Like Button -->
        <button 
          @click="handleLike"
          class="inline-flex items-center px-3 py-1.5 border shadow-sm text-xs font-medium rounded focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
          :class="post.isLiked ? 'border-indigo-600 text-indigo-600 bg-indigo-50 hover:bg-indigo-100' : 'border-gray-300 text-gray-700 bg-white hover:bg-gray-50'"
        >
          <ThumbsUp class="mr-1.5 h-3 w-3" :class="{ 'fill-current': post.isLiked }" />
          {{ post.isLiked ? 'Liked' : 'Like' }}
        </button>
      </div>

      <!-- Comments Section -->
      <div class="px-4 py-5 sm:px-6 border-t border-gray-200 bg-gray-50">
        <CommentList :postId="post.postId" />
      </div>
    </div>
  </div>
</template>
