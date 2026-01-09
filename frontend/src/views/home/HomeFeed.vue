<template>
  <div class="max-w-2xl mx-auto py-8">
    <!-- Feed -->
    <PostListSkeleton v-if="loading" :count="3" />

    <EmptyState 
      v-else-if="posts.length === 0"
      :title="$t('common.noData')"
      :description="$t('board.list.noPosts')"
      :icon="FileText"
      container-class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg transition-colors duration-200"
    />

    <div v-else>
      <FeedCard 
        v-for="post in posts" 
        :key="post.postId" 
        v-memo="[post.postId, post.liked, post.scrapped, post.viewCount, post.commentCount, post.subscribed]"
        :post="post" 
        @like="handleLike" 
        @scrap="handleScrap"
        @subscribe="handleSubscribe" 
      />

      <!-- Sentinel for infinite scroll -->
      <div ref="sentinel" class="h-10 flex justify-center items-center mt-4">
        <BaseSpinner v-if="loadingMore" size="sm" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { postApi } from '@/api/post'
import { boardApi } from '@/api/board'
import FeedCard from '@/components/feed/FeedCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostListSkeleton from '@/components/common/ui/PostListSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { FileText } from 'lucide-vue-next'
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

  const previousLiked = post.liked
  const previousCount = post.likeCount

  // Optimistic update
  post.liked = !post.liked
  post.likeCount += post.liked ? 1 : -1

  try {
    if (previousLiked) {
      await postApi.unlikePost(post.postId)
    } else {
      await postApi.likePost(post.postId)
    }
  } catch (error) {
    // Revert
    post.liked = previousLiked
    post.likeCount = previousCount
    logger.error('Failed to toggle like:', error)
  }
}

async function handleScrap(post) {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  const previousScrapped = post.scrapped

  // Optimistic update
  post.scrapped = !post.scrapped

  try {
    if (previousScrapped) {
      await postApi.unscrapPost(post.postId)
    } else {
      await postApi.scrapPost(post.postId)
    }
  } catch (error) {
    // Revert
    post.scrapped = previousScrapped
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
    const subscribed = post.subscribed

    if (subscribed) {
      await boardApi.unsubscribeBoard(boardUrl)
    } else {
      await boardApi.subscribeBoard(boardUrl)
    }

    // Update all posts from the same board
    posts.value.forEach(p => {
      if (p.boardUrl === boardUrl) {
        p.subscribed = !subscribed
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
