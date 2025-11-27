<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'
import { Users, User } from 'lucide-vue-next'

const boards = ref([])
const loading = ref(false)

const fetchBoards = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/boards')
    if (data.success) {
      boards.value = data.data
    }
  } catch (error) {
    console.error('게시판 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchBoards()
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <h1 class="text-3xl font-bold text-gray-900 mb-8">전체 게시판</h1>

    <div v-if="loading" class="text-center py-20">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <router-link
        v-for="board in boards"
        :key="board.boardUrl"
        :to="`/board/${board.boardUrl}`"
        class="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 border border-gray-100"
      >
        <div class="p-6">
          <div class="flex items-center">
            <div class="flex-shrink-0 h-12 w-12 rounded-md bg-indigo-100 flex items-center justify-center text-indigo-600 text-xl font-bold">
              {{ board.boardName.substring(0, 1) }}
            </div>
            <div class="ml-4">
              <h3 class="text-lg font-medium text-gray-900">{{ board.boardName }}</h3>
              <div class="flex items-center mt-1 text-sm text-gray-500">
                <User class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                <span>{{ board.adminDisplayName || '관리자' }}</span>
              </div>
            </div>
          </div>
          <div class="mt-4">
            <p class="text-sm text-gray-500 line-clamp-2 h-10">
              {{ board.description || '설명이 없습니다.' }}
            </p>
          </div>
          <div class="mt-4 pt-4 border-t border-gray-100 flex items-center justify-between">
             <div class="flex items-center text-sm text-gray-500">
                <Users class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                <span>구독자 {{ board.subscriberCount || 0 }}명</span>
             </div>
          </div>
        </div>
      </router-link>
    </div>
    
    <div v-if="!loading && boards.length === 0" class="text-center py-20 text-gray-500">
      등록된 게시판이 없습니다.
    </div>
  </div>
</template>
