<template>
  <div class="max-w-2xl mx-auto py-8">
    <!-- Feed -->
    <div v-if="loading" class="space-y-6">
      <div v-for="i in 3" :key="i"
        class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg h-96 animate-pulse">
      </div>
    </div>

    <div v-else-if="posts.length === 0"
      class="text-center py-12 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg transition-colors duration-200">
      <div class="mx-auto h-12 w-12 text-gray-400">
        <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </div>
      <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">{{ $t('common.noData') }}</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('board.list.noPosts') }}</p>
    </div>

    <div v-else>
      <FeedCard v-for="post in posts" :key="post.postId" :post="post" @like="handleLike" @scrap="handleScrap"
        @subscribe="handleSubscribe" />

      <!-- Sentinel for infinite scroll -->
      <div ref="sentinel" class="h-10 flex justify-center items-center mt-4">
        <div v-if="loadingMore" class="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { postApi } from '@/api/post'
import { boardApi } from '@/api/board'
import FeedCard from '@/components/feed/FeedCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import logger from '@/utils/logger'

const posts = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const page = ref(0)
const size = ref(10)
const hasMore = ref(true)
const sentinel = ref(null)
const authStore = useAuthStore()
const router = useRouter()

let observer = null

async function fetchTrendingPosts(isLoadMore = false) {
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
  }

  try {
    const { data } = await postApi.getTrendingPosts(page.value, size.value)
    if (data.success) {
      const newPosts = data.data
      if (newPosts.length < size.value) {
        hasMore.value = false
      }

      if (isLoadMore) {
        posts.value.push(...newPosts)
      } else {
        posts.value = newPosts
      }

      page.value++
    }
  } catch (error) {
    logger.error('Failed to fetch trending posts:', error)
  } finally {
    loading.value = false
    loadingMore.value = false
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
    logger.error('Failed to toggle like:', error)
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
    logger.error('Failed to toggle scrap:', error)
  }
}

async function handleSubscribe(post) {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  try {
    const boardUrl = post.boardUrl
    const isSubscribed = post.isSubscribed

    if (isSubscribed) {
      await boardApi.unsubscribeBoard(boardUrl)
    } else {
      await boardApi.subscribeBoard(boardUrl)
    }

    // Update all posts from the same board
    posts.value.forEach(p => {
      if (p.boardUrl === boardUrl) {
        p.isSubscribed = !isSubscribed
      }
    })
  } catch (error) {
    logger.error('Failed to toggle subscription:', error)
  }
}

onMounted(() => {
  fetchTrendingPosts()

  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && hasMore.value && !loading.value && !loadingMore.value) {
      fetchTrendingPosts(true)
    }
  }, {
    rootMargin: '200px'
  })

  if (sentinel.value) {
    observer.observe(sentinel.value)
  }
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
})
</script>
