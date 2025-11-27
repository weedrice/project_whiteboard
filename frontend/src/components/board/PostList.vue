<script setup>
import { defineProps } from 'vue'
import { MessageSquare, ThumbsUp, User, Clock, Image as ImageIcon } from 'lucide-vue-next'

defineProps({
  posts: {
    type: Array,
    required: true
  },
  boardUrl: { // boardId 대신 boardUrl 사용
    type: String,
    required: false
  }
})

function formatDate(dateString) {
  const date = new Date(dateString)
  const today = new Date()
  
  const isToday = date.getDate() === today.getDate() &&
                  date.getMonth() === today.getMonth() &&
                  date.getFullYear() === today.getFullYear()
                  
  if (isToday) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString()
}
</script>

<template>
  <div class="bg-white shadow overflow-hidden sm:rounded-md">
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-24">
              번호
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              제목
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-32">
              글쓴이
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-24">
              날짜
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">
              조회
            </th>
            <th scope="col" class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">
              추천
            </th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="post in posts" :key="post.postId" class="hover:bg-gray-50">
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 text-center">
              <span v-if="post.isNotice" class="font-bold text-red-600">공지</span>
              <span v-else>{{ post.postId }}</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 align-middle">
              <router-link :to="`/board/${boardUrl}/post/${post.postId}`" class="hover:text-indigo-600 flex items-center h-full">
                <span 
                  v-if="post.category && post.category.name !== '일반'" 
                  class="inline-flex items-center justify-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-800 mr-2 h-5"
                >
                  {{ post.category.name }}
                </span>
                <span v-if="post.isNotice" class="inline-flex items-center justify-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800 mr-2 h-5">
                  공지
                </span>
                <span v-if="post.hasImage" class="mr-1 text-gray-400 flex items-center">
                    <ImageIcon class="h-4 w-4" />
                </span>
                <span class="truncate">{{ post.title }}</span>
                <span v-if="post.commentCount > 0" class="ml-1 text-indigo-600 text-xs flex items-center">
                    [{{ post.commentCount }}]
                </span>
              </router-link>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ post.author?.displayName }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 text-center">
              {{ formatDate(post.createdAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 text-center">
              {{ post.viewCount }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 text-center">
              {{ post.likeCount }}
            </td>
          </tr>
          <tr v-if="posts.length === 0">
            <td colspan="6" class="px-6 py-10 text-center text-gray-500">
              게시글이 없습니다.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
