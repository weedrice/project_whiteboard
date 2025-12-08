<template>
  <div class="bg-white border border-gray-200 rounded-lg mb-6 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center p-3">
      <div class="flex items-center flex-1">
        <div class="h-8 w-8 rounded-full bg-gray-200 overflow-hidden mr-3">
          <img 
            v-if="post.authorProfileImage" 
            :src="post.authorProfileImage" 
            class="h-full w-full object-cover"
            alt="Profile"
          />
          <div v-else class="h-full w-full flex items-center justify-center bg-indigo-100 text-indigo-600 font-bold text-xs">
            {{ post.authorName?.charAt(0) }}
          </div>
        </div>
        <div>
          <p class="text-sm font-semibold text-gray-900">{{ post.authorName }}</p>
          <p class="text-xs text-gray-500">{{ formatDate(post.createdAt) }}</p>
        </div>
      </div>
      <button class="text-gray-400 hover:text-gray-600">
        <MoreHorizontal class="h-5 w-5" />
      </button>
    </div>

    <!-- Image -->
    <div class="aspect-w-1 aspect-h-1 bg-gray-100 relative">
      <img 
        v-if="post.thumbnailUrl" 
        :src="post.thumbnailUrl" 
        class="object-cover w-full h-full"
        alt="Post Image"
      />
      <div v-else class="flex items-center justify-center h-full text-gray-400">
        <ImageIcon class="h-12 w-12" />
      </div>
    </div>

    <!-- Actions -->
    <div class="p-3">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center space-x-4">
          <button 
            @click="$emit('like', post)" 
            class="focus:outline-none transition-colors duration-200"
            :class="post.isLiked ? 'text-red-500' : 'text-gray-900 hover:text-gray-600'"
          >
            <Heart class="h-6 w-6" :class="{ 'fill-current': post.isLiked }" />
          </button>
          <button class="text-gray-900 hover:text-gray-600 focus:outline-none">
            <MessageCircle class="h-6 w-6" />
          </button>
          <button class="text-gray-900 hover:text-gray-600 focus:outline-none">
            <Send class="h-6 w-6" />
          </button>
        </div>
        <button 
          @click="$emit('scrap', post)"
          class="focus:outline-none transition-colors duration-200"
          :class="post.isScrapped ? 'text-yellow-500' : 'text-gray-900 hover:text-gray-600'"
        >
          <Bookmark class="h-6 w-6" :class="{ 'fill-current': post.isScrapped }" />
        </button>
      </div>

      <!-- Likes -->
      <p class="text-sm font-semibold text-gray-900 mb-2">
        {{ $t('board.feed.likes', { count: post.likeCount }) }}
      </p>

      <!-- Caption -->
      <div class="mb-2">
        <span class="text-sm font-semibold text-gray-900 mr-2">{{ post.authorName }}</span>
        <span class="text-sm text-gray-900">{{ post.title }}</span>
      </div>
      
      <!-- View all comments -->
      <router-link 
        :to="`/board/${post.boardUrl}/post/${post.postId}`"
        class="text-sm text-gray-500 mb-2 block"
      >
        {{ $t('board.feed.viewAllComments', { count: post.commentCount }) }}
      </router-link>

      <!-- Timestamp -->
      <p class="text-xs text-gray-400 uppercase">
        {{ formatTimeAgo(post.createdAt) }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { Heart, MessageCircle, Send, Bookmark, MoreHorizontal, Image as ImageIcon } from 'lucide-vue-next'
import { formatDistanceToNow } from 'date-fns'
import { ko } from 'date-fns/locale'

const props = defineProps({
  post: {
    type: Object,
    required: true
  }
})

defineEmits(['like', 'scrap'])

function formatDate(dateString) {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString()
}

function formatTimeAgo(dateString) {
  if (!dateString) return ''
  return formatDistanceToNow(new Date(dateString), { addSuffix: true, locale: ko })
}
</script>
