<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from '@/api'
import { Users, User } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const boards = ref([])
const loading = ref(false)
const authStore = useAuthStore()

const fetchBoards = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/boards')
    if (data.success) {
      boards.value = data.data
    }
  } catch (error) {
    logger.error('게시판 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const subscribedBoards = computed(() => {
  return boards.value.filter(board => board.isSubscribed)
})

onMounted(() => {
  fetchBoards()
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">

    <div v-if="loading" class="text-center py-20">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else>
      <!-- Subscribed Boards -->
      <div v-if="authStore.isAuthenticated && subscribedBoards.length > 0" class="mb-12">
        <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">{{ $t('board.list.subscribed') }}</h2>
        <div class="flex space-x-6 overflow-x-auto pb-4">
          <router-link
            v-for="board in subscribedBoards"
            :key="board.boardUrl"
            :to="`/board/${board.boardUrl}`"
            class="flex flex-col items-center group flex-shrink-0 min-w-[80px]"
          >
            <div class="h-20 w-20 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center group-hover:ring-2 ring-indigo-500 transition-all overflow-hidden border border-gray-200 dark:border-gray-700 shadow-sm">
               <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-contain bg-white dark:bg-gray-800" alt="" />
               <span v-else class="text-indigo-600 dark:text-indigo-400 font-bold text-2xl">{{ board.boardName[0] }}</span>
            </div>
            <span class="mt-3 text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 text-center truncate w-full">{{ board.boardName }}</span>
          </router-link>
        </div>
      </div>

      <!-- All Boards -->
      <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6" v-if="authStore.isAuthenticated && subscribedBoards.length > 0">{{ $t('board.list.title') }}</h2>
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <router-link
          v-for="board in boards"
          :key="board.boardUrl"
          :to="`/board/${board.boardUrl}`"
          class="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg hover:shadow-md transition-all duration-200 border border-gray-100 dark:border-gray-700"
        >
          <div class="p-6">
            <div class="flex items-center">
              <div class="flex-shrink-0 h-12 w-12 rounded-md bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 text-xl font-bold overflow-hidden border border-gray-200 dark:border-gray-600">
                <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-contain bg-white dark:bg-gray-700" alt="" />
                <span v-else>{{ board.boardName.substring(0, 1) }}</span>
              </div>
              <div class="ml-4">
                <h3 class="text-lg font-medium text-gray-900 dark:text-white">{{ board.boardName }}</h3>
                <div class="flex items-center mt-1 text-sm text-gray-500 dark:text-gray-400">
                  <User class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  <span>{{ board.adminDisplayName || $t('common.admin') }}</span>
                </div>
              </div>
            </div>
            <div class="mt-4">
              <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 h-10">
                {{ board.description || $t('board.list.noDesc') }}
              </p>
            </div>
            <div class="mt-4 pt-4 border-t border-gray-100 dark:border-gray-700 flex items-center justify-between">
               <div class="flex items-center text-sm text-gray-500 dark:text-gray-400">
                  <Users class="flex-shrink-0 mr-1.5 h-4 w-4 text-gray-400" />
                  <span>{{ $t('board.list.subscribers', { count: board.subscriberCount || 0 }) }}</span>
               </div>
            </div>
          </div>
        </router-link>
      </div>
      
      <div v-if="boards.length === 0" class="text-center py-20 text-gray-500 dark:text-gray-400">
        {{ $t('board.list.empty') }}
      </div>
    </div>
  </div>
</template>
