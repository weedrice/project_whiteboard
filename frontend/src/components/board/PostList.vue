<script setup>
import { defineProps, defineEmits } from 'vue'
import { MessageSquare, ThumbsUp, User, Clock, Image as ImageIcon, ArrowUp, ArrowDown } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'

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
  },
  currentSort: {
      type: String,
      default: 'createdAt,desc'
  },
  showBoardName: {
    type: Boolean,
    default: false
  },
  hideNoColumn: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:sort'])

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

function handleSort(field) {
    const [currentField, currentDirection] = props.currentSort.split(',')
    let newDirection = 'desc'
    
    if (field === currentField) {
        newDirection = currentDirection === 'desc' ? 'asc' : 'desc'
    }
    
    emit('update:sort', `${field},${newDirection}`)
}
</script>

<template>
  <div class="card">
    <div class="table-container">
      <table class="table-base">
        <thead class="table-head">
          <tr>
            <th v-if="!hideNoColumn" scope="col" class="table-th text-center w-24 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors duration-200" @click="handleSort('postId')">
              <div class="flex items-center justify-center">
                {{ $t('common.no') }}
                <component 
                  v-if="currentSort.startsWith('postId')" 
                  :is="currentSort.endsWith('desc') ? ArrowDown : ArrowUp" 
                  class="h-4 w-4 ml-1" 
                />
              </div>
            </th>
            <th v-if="showBoardName" scope="col" class="table-th text-center w-32">
              {{ $t('common.board') }}
            </th>
            <th scope="col" class="table-th text-center">
              {{ $t('common.title') }}
            </th>
            <th scope="col" class="table-th text-center w-32">
              {{ $t('common.author') }}
            </th>
            <th scope="col" class="table-th text-center w-28 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors duration-200" @click="handleSort('createdAt')">
              <div class="flex items-center justify-center">
                {{ $t('common.date') }}
                <component 
                  v-if="currentSort.startsWith('createdAt')" 
                  :is="currentSort.endsWith('desc') ? ArrowDown : ArrowUp" 
                  class="h-4 w-4 ml-1" 
                />
              </div>
            </th>
            <th scope="col" class="table-th text-center w-24 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors duration-200" @click="handleSort('viewCount')">
              <div class="flex items-center justify-center">
                {{ $t('common.views') }}
                <component 
                  v-if="currentSort.startsWith('viewCount')" 
                  :is="currentSort.endsWith('desc') ? ArrowDown : ArrowUp" 
                  class="h-4 w-4 ml-1" 
                />
              </div>
            </th>
            <th scope="col" class="table-th text-center w-24 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors duration-200" @click="handleSort('likeCount')">
               <div class="flex items-center justify-center">
                {{ $t('common.likes') }}
                <component 
                  v-if="currentSort.startsWith('likeCount')" 
                  :is="currentSort.endsWith('desc') ? ArrowDown : ArrowUp" 
                  class="h-4 w-4 ml-1" 
                />
              </div>
            </th>
          </tr>
        </thead>
        <tbody class="table-body">
          <tr v-for="(post, index) in posts" :key="post.postId" :class="['table-row', post.isNotice ? 'bg-gray-50 dark:bg-gray-900/50' : '']">
            <td v-if="!hideNoColumn" class="table-td text-center">
              <span v-if="post.isNotice" class="font-bold text-red-600 dark:text-red-400">{{ $t('common.notice') }}</span>
              <span v-else>{{ post.postId }}</span>
            </td>
            <td v-if="showBoardName" class="table-td text-center">
              {{ post.boardName || '-' }}
            </td>
            <td class="table-td font-medium text-gray-900 dark:text-gray-100 align-middle">
              <router-link 
                :to="`/board/${boardUrl || post.boardUrl}/post/${post.postId}`" 
                class="hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center h-full"
                v-if="boardUrl || post.boardUrl"
              >
                <span 
                  v-if="post.category && post.category.name !== '일반'" 
                  class="badge badge-gray"
                >
                  {{ post.category.name }}
                </span>
                <span v-if="post.isNotice" class="badge badge-red">
                  {{ $t('common.notice') }}
                </span>
                <span v-if="post.hasImage" class="mr-1 text-gray-400 flex items-center">
                    <ImageIcon class="h-4 w-4" />
                </span>
                <span class="truncate">{{ post.title }}</span>
                <span v-if="post.commentCount > 0" class="ml-1 text-indigo-600 dark:text-indigo-400 text-xs flex items-center">
                    [{{ post.commentCount }}]
                </span>
              </router-link>
              <span v-else class="text-gray-400 flex items-center h-full cursor-not-allowed" :title="$t('board.invalidUrl')">
                  <span class="truncate">{{ post.title }}</span>
              </span>
            </td>
            <td class="table-td text-center text-gray-900 dark:text-gray-200">
              <UserMenu 
                v-if="post.author"
                :user-id="post.author.userId"
                :display-name="post.author.displayName"
              />
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
            <td colspan="6" class="px-6 py-10 text-center text-gray-500 dark:text-gray-400">
              {{ $t('board.list.noPosts') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>