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
  if (!confirm('정말 삭제하시겠습니까?')) return

  try {
    const { data } = await postApi.deletePost(route.params.postId)
    if (data.success) {
      router.push(`/board/${post.value.board.boardUrl}`)
    }
  } catch (err) {
    console.error('게시글 삭제 실패:', err)
    alert('게시글 삭제에 실패했습니다.')
  }
}

async function handleLike() {
  if (!authStore.isAuthenticated) {
    alert('로그인 한 사용자만 사용할 수 있습니다.')
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
    console.error('좋아요 처리에 실패했습니다:', err)
  }
}

async function handleScrap() {
  if (!authStore.isAuthenticated) {
    alert('로그인 한 사용자만 사용할 수 있습니다.')
    return
  }

  try {
    // Since we don't have isScrapped in PostResponse yet, we'll just try to scrap.
    // If already scrapped, backend might throw error or handle it.
    // Ideally we need isScrapped in PostResponse.
    // For now, let's assume it's a toggle or just scrap.
    // PostController has scrapPost and unscrapPost.
    // I'll implement a simple toggle if I can track state, otherwise just scrap.
    // But to be proper, I should add isScrapped to PostResponse.
    // I will add isScrapped to PostResponse.java as well.
    
    if (post.value.isScrapped) {
        await postApi.unscrapPost(route.params.postId)
        post.value.isScrapped = false
        alert('스크랩이 취소되었습니다.')
    } else {
        await postApi.scrapPost(route.params.postId)
        post.value.isScrapped = true
        alert('스크랩되었습니다.')
    }
  } catch (err) {
    console.error('스크랩 처리 실패:', err)
    alert('스크랩 처리에 실패했습니다.')
  }
}

onMounted(fetchPost)
</script>

<template>
  <div class="w-full bg-white shadow overflow-hidden sm:rounded-lg">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
      <div class="mt-4">
        <button @click="router.back()" class="text-indigo-600 hover:text-indigo-500">
          뒤로 가기
        </button>
      </div>
    </div>

    <div v-else-if="post">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200">
        <div class="flex items-center justify-between">
          <button 
            @click="router.push(`/board/${post.board.boardUrl}`)" 
            class="inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft class="h-4 w-4 mr-1" />
            목록으로
          </button>
          
          <div class="flex space-x-2">
             <router-link 
              v-if="isAuthor"
              :to="`/board/${post.board.boardUrl}/post/${post.postId}/edit`"
              class="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
            >
              수정
            </router-link>
            <button 
              v-if="isAuthor || isAdmin"
              @click="handleDelete"
              class="inline-flex items-center px-3 py-1.5 border border-transparent shadow-sm text-xs font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 cursor-pointer"
            >
              삭제
            </button>
          </div>
        </div>
        
        <h1 class="mt-4 text-2xl font-bold text-gray-900 flex items-center">
          <span v-if="post.category && post.category.name !== '일반'" class="inline-flex items-center px-2.5 py-0.5 rounded-md text-sm font-medium bg-gray-100 text-gray-800 mr-2">
            {{ post.category.name }}
          </span>
          {{ post.title }}
        </h1>
        
        <div class="mt-2 flex items-center text-sm text-gray-500 space-x-4">
          <span class="flex items-center">
            <User class="h-4 w-4 mr-1" />
            {{ post.author.displayName }}
          </span>
          <span class="flex items-center">
            <Clock class="h-4 w-4 mr-1" />
            {{ formatDate(post.createdAt) }}
          </span>
          <span class="flex items-center">
            <Eye class="h-4 w-4 mr-1" />
            {{ post.viewCount }}
          </span>
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
      <div class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-gray-50 flex items-center justify-center space-x-8">
          <button 
            @click="handleLike" 
            :disabled="!authStore.isAuthenticated"
            class="flex flex-col items-center group cursor-pointer"
            :class="{'text-indigo-600': post.isLiked, 'text-gray-500': !post.isLiked, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated}"
          >
              <div class="p-2 rounded-full group-hover:bg-indigo-50 transition-colors">
                  <ThumbsUp class="h-6 w-6" :class="{'fill-current': post.isLiked}" />
              </div>
              <span class="text-sm font-medium mt-1">{{ post.likeCount }}</span>
          </button>
          
          <button 
            @click="handleScrap"
            :disabled="!authStore.isAuthenticated"
            class="flex flex-col items-center group text-gray-500 hover:text-yellow-600 cursor-pointer"
            :class="{'opacity-50 cursor-not-allowed': !authStore.isAuthenticated}"
          >
              <div class="p-2 rounded-full group-hover:bg-yellow-50 transition-colors">
                  <Bookmark class="h-6 w-6" />
              </div>
              <span class="text-sm font-medium mt-1">스크랩</span>
          </button>
      </div>

      <!-- Comments Section -->
      <div class="border-t border-gray-200 mt-6">
        <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
      </div>
    </div>
  </div>
</template>
