<template>
  <div class="max-w-2xl mx-auto py-8">
    <!-- Feed -->
    <PostListSkeleton v-if="loading" :count="3" />

    <EmptyState v-else-if="posts.length === 0" :title="$t('common.noData')" :description="$t('board.list.noPosts')"
      :icon="FileText"
      container-class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg transition-colors duration-200" />

    <div v-else>
      <template v-for="post in posts" :key="post?.postId">
        <FeedCard v-if="post && post.postId"
          v-memo="[post.postId, post.liked, post.scrapped, post.viewCount, post.commentCount, post.subscribed]"
          :post="post" @like="handleLike" @scrap="handleScrap" @subscribe="handleSubscribe" />
      </template>

      <!-- Sentinel for infinite scroll -->
      <div ref="sentinel" class="h-10 flex justify-center items-center mt-4">
        <BaseSpinner v-if="loadingMore" size="sm" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useTrendingPosts } from '@/composables/useTrendingPosts'
import { usePost } from '@/composables/usePost'
import { useBoard } from '@/composables/useBoard'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useIntersectionObserver } from '@/composables/useIntersectionObserverRef'
import FeedCard from '@/components/feed/FeedCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostListSkeleton from '@/components/common/ui/PostListSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { FileText } from 'lucide-vue-next'
import type { FeedPost, PostSummary } from '@/types'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

useHead({
  title: '홈',
  meta: [
    { name: 'description', content: '노비스 - 다양한 주제의 게시판에서 인기 게시글을 발견하고, 의견을 나누는 커뮤니티. 지금 가입하고 관심 게시판을 구독하세요.' },
    { property: 'og:title', content: '노비스' },
    { property: 'og:description', content: '노비스 - 다양한 주제의 게시판에서 인기 게시글을 발견하고, 의견을 나누는 커뮤니티. 지금 가입하고 관심 게시판을 구독하세요.' }
  ]
})

// Infinite scroll for trending posts
const { posts: rawPosts, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage } = useTrendingPosts(10)

// Convert PostSummary to FeedPost (filter required fields, then map)
const posts = computed<FeedPost[]>(() => {
  return rawPosts.value
    .filter((post): post is PostSummary & { boardUrl: string; boardName: string } =>
      post != null &&
      post.postId != null &&
      post.boardUrl != null &&
      post.boardName != null &&
      (post.authorName != null || post.author?.displayName != null)
    )
    .map((post): FeedPost => ({
      ...post,
      boardUrl: post.boardUrl,
      boardName: post.boardName,
      authorName: post.authorName ?? post.author?.displayName ?? '',
      liked: post.liked ?? false,
      scrapped: post.scrapped ?? false,
      subscribed: post.subscribed ?? false
    }))
})

// Mutations
const { useLikePost, useUnlikePost, useScrapPost, useUnscrapPost } = usePost()
const { useSubscribeBoard } = useBoard()
const { requireAuth } = useAuthGuard()

const likePost = useLikePost()
const unlikePost = useUnlikePost()
const scrapPost = useScrapPost()
const unscrapPost = useUnscrapPost()
const subscribeBoard = useSubscribeBoard()

// Sentinel for infinite scroll
const sentinel = ref<HTMLElement | null>(null)
const { isIntersecting } = useIntersectionObserver(sentinel, {
  rootMargin: '200px',
  threshold: 0.1
})

// Auto-load next page when sentinel is visible
watch([isIntersecting, hasNextPage, isFetchingNextPage],
  ([intersecting, hasNext, isFetching]) => {
    if (intersecting && hasNext && !isFetching) {
      fetchNextPage()
    }
  }
)

const loading = computed(() => isLoading.value)
const loadingMore = computed(() => isFetchingNextPage.value)

async function handleLike(post: FeedPost) {
  if (!requireAuth()) return

  if (post.liked) {
    unlikePost.mutate(post.postId)
  } else {
    likePost.mutate(post.postId)
  }
}

async function handleScrap(post: FeedPost) {
  if (!requireAuth()) return

  if (post.scrapped) {
    unscrapPost.mutate(post.postId)
  } else {
    scrapPost.mutate(post.postId)
  }
}

async function handleSubscribe(post: FeedPost) {
  if (!requireAuth()) return

  if (post.subscribed && !window.confirm(t('user.subscriptions.unsubscribeConfirm'))) return

  subscribeBoard.mutate({
    boardUrl: post.boardUrl as string,
    isSubscribed: post.subscribed
  })
}
</script>
