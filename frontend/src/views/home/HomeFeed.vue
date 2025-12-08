<template>
  <div class="max-w-lg mx-auto py-8">
    <!-- Stories (Placeholder) -->
    <div class="bg-white border border-gray-200 rounded-lg p-4 mb-6 overflow-x-auto whitespace-nowrap scrollbar-hide">
      <div class="inline-flex space-x-4">
        <div v-for="i in 5" :key="i" class="flex flex-col items-center space-y-1">
          <div class="h-16 w-16 rounded-full ring-2 ring-pink-500 p-0.5">
            <div class="h-full w-full rounded-full bg-gray-200"></div>
          </div>
          <span class="text-xs text-gray-500">User {{ i }}</span>
        </div>
      </div>
    </div>

    <!-- Feed -->
    <div v-if="loading" class="space-y-6">
      <div v-for="i in 3" :key="i" class="bg-white border border-gray-200 rounded-lg h-96 animate-pulse"></div>
    </div>

    <div v-else-if="posts.length === 0" class="text-center py-12 bg-white border border-gray-200 rounded-lg">
      <div class="mx-auto h-12 w-12 text-gray-400">
        <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </div>
      <h3 class="mt-2 text-sm font-medium text-gray-900">{{ $t('common.noData') }}</h3>
      <p class="mt-1 text-sm text-gray-500">{{ $t('board.list.noPosts') }}</p>
    </div>

    <div v-else>
      <FeedCard
        v-for="post in posts"
        :key="post.postId"
        :post="post"
        @like="handleLike"
        @scrap="handleScrap"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { postApi } from '@/api/post'
import FeedCard from '@/components/feed/FeedCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const posts = ref([])
const loading = ref(true)
const authStore = useAuthStore()
const router = useRouter()

async function fetchTrendingPosts() {
  loading.value = true
  try {
    const { data } = await postApi.getTrendingPosts(20)
    if (data.success) {
      posts.value = data.data
    }
  } catch (error) {
    console.error('Failed to fetch trending posts:', error)
  } finally {
    loading.value = false
  }
}

async function handleLike(post) {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  try {
    if (post.isLiked) {
      await postApi.unlikePost(post.postId)
      post.isLiked = false
      post.likeCount--
    } else {
      await postApi.likePost(post.postId)
      post.isLiked = true
      post.likeCount++
    }
  } catch (error) {
    console.error('Failed to toggle like:', error)
  }
}

async function handleScrap(post) {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  try {
    if (post.isScrapped) {
      await postApi.unscrapPost(post.postId)
      post.isScrapped = false
    } else {
      await postApi.scrapPost(post.postId)
      post.isScrapped = true
    }
  } catch (error) {
    console.error('Failed to toggle scrap:', error)
  }
}

onMounted(() => {
  fetchTrendingPosts()
})
</script>
