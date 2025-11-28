<script setup>
import { defineProps, computed } from 'vue'
import { MessageSquare, ThumbsUp, User, Clock, Image as ImageIcon } from 'lucide-vue-next'

const props = defineProps({
  posts: {
    type: Array,
    required: true
  },
  boardUrl: { // boardId 대신 boardUrl 사용
    type: String,
    required: false
  },
  // Pagination props for numbering
  totalCount: {
    type: Number,
    default: 0
  },
  page: {
    type: Number,
    default: 0
  },
  size: {
    type: Number,
    default: 20
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

function getPostNumber(index) {
    // 1부터 시작하는 오름차순 번호
    return (props.page * props.size) + index + 1;
}
</script>

<template>
  <div class="card">
    <div class="table-container">
      <table class="table-base">
        <thead class="table-head">
          <tr>
            <th scope="col" class="table-th text-center w-24">
              번호
            </th>
            <th scope="col" class="table-th text-left">
              제목
            </th>
            <th scope="col" class="table-th text-center w-32">
              글쓴이
            </th>
            <th scope="col" class="table-th text-center w-24">
              날짜
            </th>
            <th scope="col" class="table-th text-center w-20">
              조회
            </th>
            <th scope="col" class="table-th text-center w-20">
              추천
            </th>
          </tr>
        </thead>
        <tbody class="table-body">
          <tr v-for="(post, index) in posts" :key="post.postId" :class="['table-row', post.isNotice ? 'bg-gray-50' : '']">
            <td class="table-td text-center">
              <span v-if="post.isNotice" class="font-bold text-red-600">공지</span>
              <span v-else>{{ getPostNumber(index) }}</span>
            </td>
            <td class="table-td font-medium text-gray-900 align-middle">
              <router-link 
                :to="`/board/${boardUrl || post.boardUrl}/post/${post.postId}`" 
                class="hover:text-indigo-600 flex items-center h-full"
                v-if="boardUrl || post.boardUrl"
              >
                <span 
                  v-if="post.category && post.category.name !== '일반'" 
                  class="badge badge-gray"
                >
                  {{ post.category.name }}
                </span>
                <span v-if="post.isNotice" class="badge badge-red">
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
              <span v-else class="text-gray-400 flex items-center h-full cursor-not-allowed" title="Invalid Board URL">
                  <span class="truncate">{{ post.title }}</span>
              </span>
            </td>
            <td class="table-td text-center">
              {{ post.author?.displayName }}
            </td>
            <td class="table-td text-center">
              {{ formatDate(post.createdAt) }}
            </td>
            <td class="table-td text-center">
              {{ post.viewCount }}
            </td>
            <td class="table-td text-center">
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
