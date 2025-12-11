<template>
  <div
    class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg mb-6 overflow-hidden transition-colors duration-200">
    <!-- Header -->
    <div class="flex items-center justify-between p-3 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center space-x-2">
        <router-link :to="`/board/${post.boardUrl}`" class="flex items-center space-x-2 group">
          <!-- Board Thumbnail -->
          <div
            class="h-6 w-6 rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden group-hover:opacity-80 transition-opacity">
            <img v-if="post.boardIconUrl" :src="post.boardIconUrl" class="h-full w-full object-cover" alt="Board" />
            <div v-else
              class="h-full w-full flex items-center justify-center bg-indigo-100 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-400 font-bold text-[10px]">
              {{ post.boardName?.charAt(0) }}
            </div>
          </div>
          <!-- Board Name -->
          <span class="text-sm font-semibold text-gray-900 dark:text-gray-100 group-hover:underline">{{ post.boardName
          }}</span>
        </router-link>
        <span class="text-gray-300 dark:text-gray-600">|</span>
        <!-- Date -->
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(post.createdAt) }}</span>
      </div>

      <div class="flex items-center space-x-3">
        <!-- Subscribe Button -->
        <button @click="$emit('subscribe', post)"
          class="text-xs font-medium focus:outline-none transition-colors duration-200"
          :class="post.isSubscribed ? 'text-gray-500 dark:text-gray-400' : 'text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300'">
          {{ post.isSubscribed ? $t('common.subscribed') : $t('common.subscribe') }}
        </button>
        <!-- Scrap Button -->
        <button @click="$emit('scrap', post)" class="focus:outline-none transition-colors duration-200"
          :class="post.isScrapped ? 'text-yellow-500' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'">
          <Bookmark class="h-5 w-5" :class="{ 'fill-current': post.isScrapped }" />
        </button>
      </div>
    </div>

    <!-- Body (Image) -->
    <!-- Body (Image) -->
    <router-link :to="`/board/${post.boardUrl}/post/${post.postId}`"
      class="block aspect-w-1 aspect-h-1 bg-gray-100 dark:bg-gray-900 relative cursor-pointer">
      <img v-if="post.thumbnailUrl" :src="post.thumbnailUrl" class="object-cover w-full h-full" alt="Post Image" />
      <div v-else class="flex items-center justify-center h-full text-gray-400 dark:text-gray-600">
        <ImageIcon class="h-12 w-12" />
      </div>
    </router-link>

    <!-- Footer (Actions) -->
    <div class="p-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center space-x-6">
          <!-- Like -->
          <button @click="$emit('like', post)"
            class="flex items-center space-x-1 focus:outline-none transition-colors duration-200 group"
            :class="post.isLiked ? 'text-red-500' : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'">
            <ThumbsUp class="h-6 w-6" :class="{ 'fill-current': post.isLiked }" />
            <span class="text-sm font-medium">{{ post.likeCount }}</span>
          </button>

          <!-- Comment -->
          <router-link :to="`/board/${post.boardUrl}/post/${post.postId}`"
            class="flex items-center space-x-1 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 focus:outline-none group">
            <MessageCircle class="h-6 w-6" />
            <span class="text-sm font-medium">{{ post.commentCount }}</span>
          </router-link>
        </div>

        <!-- Share -->
        <button
          class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 focus:outline-none">
          <Share2 class="h-6 w-6" />
        </button>
      </div>

      <!-- Title (Optional, keeping it for context) -->
      <!-- Title (Optional, keeping it for context) -->
      <div class="mt-3">
        <span class="text-sm font-semibold text-gray-900 dark:text-white mr-2">{{ post.authorName }}</span>
        <router-link :to="`/board/${post.boardUrl}/post/${post.postId}`"
          class="text-sm text-gray-900 dark:text-gray-300 hover:underline cursor-pointer">
          {{ post.title }}
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ThumbsUp, MessageCircle, Bookmark, Image as ImageIcon, Share2 } from 'lucide-vue-next'

interface FeedPost {
  postId: number
  boardUrl: string | number
  boardName: string
  boardIconUrl?: string
  title: string
  authorName: string
  createdAt: string
  viewCount: number
  likeCount: number
  commentCount: number
  thumbnailUrl?: string
  isLiked: boolean
  isScrapped: boolean
  isSubscribed: boolean
}

const props = defineProps<{
  post: FeedPost
}>()

const emit = defineEmits<{
  (e: 'like', post: FeedPost): void
  (e: 'scrap', post: FeedPost): void
  (e: 'subscribe', post: FeedPost): void
}>()

function formatDate(dateString: string) {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString()
}
</script>
