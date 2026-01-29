<script setup lang="ts">
import { ThumbsUp, MessageSquare, Bookmark, User, Eye } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import { useRouter } from 'vue-router'
import type { FeedPost } from '@/types'

const router = useRouter()

const props = defineProps<{
  post: FeedPost
}>()

// Validate post data
if (!props.post || !props.post.postId) {
  console.warn('FeedCard: Invalid post data', props.post)
}

const emit = defineEmits<{
  (e: 'like', post: FeedPost): void
  (e: 'scrap', post: FeedPost): void
  (e: 'subscribe', post: FeedPost): void
}>()

import { formatDateOnly } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
</script>

<template>
  <BaseCard v-if="post && post.postId" noPadding class="mb-6">
    <!-- Header -->
    <div class="px-3 sm:px-6 py-3 sm:py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
      <div class="flex items-center space-x-2 sm:space-x-3">
        <!-- Board Info -->
        <div class="flex-shrink-0">
          <img v-if="post.boardIconUrl" :src="getOptimizedBoardIconUrl(post.boardIconUrl)" alt="Board Icon" class="h-7 w-7 sm:h-8 sm:w-8 rounded-full"
            loading="lazy"
            @error="handleImageError($event)" />
          <div v-else class="h-7 w-7 sm:h-8 sm:w-8 rounded-full bg-indigo-100 dark:bg-indigo-900 flex items-center justify-center">
            <span class="text-[10px] sm:text-xs font-bold text-indigo-600 dark:text-indigo-300">{{ post.boardName?.substring(0, 1)
            }}</span>
          </div>
        </div>
        <div class="min-w-0">
          <h3 class="text-xs sm:text-sm font-medium text-gray-900 dark:text-white truncate">
            <router-link :to="`/board/${post.boardUrl}`" class="hover:underline">
              {{ post.boardName }}
            </router-link>
          </h3>
          <p class="text-[10px] sm:text-xs text-gray-500 dark:text-gray-400">{{ formatDateOnly(post.createdAt) }}</p>
        </div>
      </div>
      <div class="flex items-center space-x-2">
        <BaseButton @click="$emit('subscribe', post)" variant="ghost" size="sm"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.subscribed, 'text-gray-400 dark:text-gray-500': !post.subscribed }">
          {{ post.subscribed ? $t('common.subscribed') : $t('common.subscribe') }}
        </BaseButton>
      </div>
    </div>

    <!-- Content -->
    <div class="px-4 py-4 sm:px-6 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
      @click="router.push(`/board/${post.boardUrl}/post/${post.postId}`)">
      <h2 class="text-base sm:text-lg font-bold text-gray-900 dark:text-white mb-2">{{ post.title }}</h2>
      <p class="text-xs sm:text-sm text-gray-600 dark:text-gray-300 line-clamp-[10] mb-4">{{ post.summary }}</p>

      <!-- Thumbnail if exists - fixed height block so image stays vertically centered -->
      <div v-if="post.thumbnailUrl"
        class="mb-4 rounded-lg overflow-hidden w-full bg-gray-100 dark:bg-gray-900 h-[160px] sm:h-[220px] flex items-center justify-center">
        <img :src="getOptimizedPostImageUrl(post.thumbnailUrl)" alt="Post Thumbnail"
          class="max-w-full max-h-[140px] sm:max-h-[200px] w-auto h-auto object-contain"
          loading="lazy"
          @error="handleImageError($event)" />
      </div>

      <div class="flex items-center text-[11px] sm:text-xs text-gray-500 dark:text-gray-400 space-x-4">
        <div class="flex items-center">
          <User class="h-3 w-3 mr-1" />
          <span>{{ post.authorName }}</span>
        </div>
        <div class="flex items-center">
          <Eye class="h-3 w-3 mr-1" />
          <span>{{ post.viewCount }}</span>
        </div>
      </div>
    </div>

    <!-- Footer Actions -->
    <div
      class="px-3 sm:px-6 py-2.5 sm:py-3 bg-gray-50 dark:bg-gray-900/50 border-t border-gray-200 dark:border-gray-700 flex justify-around text-xs sm:text-sm">
      <BaseButton @click.stop="$emit('like', post)" variant="ghost" size="sm" class="flex-1 justify-center min-w-0"
        :class="{ 'text-indigo-600 dark:text-indigo-400': post.liked, 'text-gray-500 dark:text-gray-400': !post.liked }">
        <ThumbsUp class="h-4 w-4 mr-1.5" :class="{ 'fill-current': post.liked }" />
        <span>{{ post.likeCount }}</span>
      </BaseButton>

      <BaseButton @click.stop="router.push(`/board/${post.boardUrl}/post/${post.postId}#comments`)" variant="ghost"
        size="sm" class="flex-1 justify-center text-gray-500 dark:text-gray-400">
        <MessageSquare class="h-4 w-4 mr-1.5" />
        <span>{{ post.commentCount }}</span>
      </BaseButton>

      <BaseButton @click.stop="$emit('scrap', post)" variant="ghost" size="sm" class="flex-1 justify-center"
        :class="{ 'text-yellow-500': post.scrapped, 'text-gray-500 dark:text-gray-400': !post.scrapped }">
        <Bookmark class="h-4 w-4 mr-1.5" :class="{ 'fill-current': post.scrapped }" />
        <span>{{ $t('common.scrap') }}</span>
      </BaseButton>
    </div>
  </BaseCard>
</template>
