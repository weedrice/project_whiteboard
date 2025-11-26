<script setup>
import { defineProps } from 'vue'
import { MessageSquare, ThumbsUp, User, Clock } from 'lucide-vue-next'

defineProps({
  posts: {
    type: Array,
    required: true
  }
})

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString()
}
</script>

<template>
  <div class="bg-white shadow overflow-hidden sm:rounded-md">
    <ul role="list" class="divide-y divide-gray-200">
      <li v-for="post in posts" :key="post.postId">
        <router-link :to="`/board/${post.boardId}/post/${post.postId}`" class="block hover:bg-gray-50">
          <div class="px-4 py-4 sm:px-6">
            <div class="flex items-center justify-between">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ post.title }}
              </p>
              <div class="ml-2 flex-shrink-0 flex">
                <p class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                  {{ post.categoryName || 'General' }}
                </p>
              </div>
            </div>
            <div class="mt-2 sm:flex sm:justify-between">
              <div class="sm:flex">
                <p class="flex items-center text-sm text-gray-500">
                  <User class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  {{ post.authorName }}
                </p>
                <p class="mt-2 flex items-center text-sm text-gray-500 sm:mt-0 sm:ml-6">
                  <Clock class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  {{ formatDate(post.createdAt) }}
                </p>
              </div>
              <div class="mt-2 flex items-center text-sm text-gray-500 sm:mt-0">
                <p class="flex items-center mr-4">
                  <ThumbsUp class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  {{ post.likeCount }}
                </p>
                <p class="flex items-center">
                  <MessageSquare class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  {{ post.commentCount }}
                </p>
              </div>
            </div>
          </div>
        </router-link>
      </li>
      <li v-if="posts.length === 0" class="px-4 py-4 sm:px-6 text-center text-gray-500">
        No posts found.
      </li>
    </ul>
  </div>
</template>
