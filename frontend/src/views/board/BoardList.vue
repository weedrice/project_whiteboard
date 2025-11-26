<script setup>
import { ref, onMounted } from 'vue'
import { boardApi } from '@/api/board'
import { Users, PlusCircle } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const boards = ref([])
const isLoading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const { data } = await boardApi.getBoards()
    if (data.success) {
      boards.value = data.data
    }
  } catch (err) {
    error.value = 'Failed to load boards.'
    console.error(err)
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-gray-900">Boards</h2>
      <router-link 
        v-if="authStore.isAuthenticated"
        to="/board/create"
        class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
      >
        <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
        New Board
      </router-link>
    </div>
    
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <router-link
        v-for="board in boards"
        :key="board.boardId"
        :to="`/board/${board.boardId}`"
        class="relative rounded-lg border border-gray-300 bg-white px-6 py-5 shadow-sm flex items-center space-x-3 hover:border-gray-400 focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-indigo-500"
      >
        <div class="flex-shrink-0">
          <img v-if="board.iconUrl" :src="board.iconUrl" class="h-10 w-10 rounded-full" alt="" />
          <div v-else class="h-10 w-10 rounded-full bg-indigo-100 flex items-center justify-center">
            <span class="text-indigo-600 font-bold text-lg">{{ board.boardName[0] }}</span>
          </div>
        </div>
        <div class="flex-1 min-w-0">
          <a href="#" class="focus:outline-none">
            <span class="absolute inset-0" aria-hidden="true" />
            <p class="text-sm font-medium text-gray-900">
              {{ board.boardName }}
            </p>
            <p class="text-sm text-gray-500 truncate">
              {{ board.description }}
            </p>
          </a>
        </div>
        <div class="flex-shrink-0 text-gray-400 text-xs flex items-center">
          <Users class="h-3 w-3 mr-1" />
          {{ board.subscriberCount }}
        </div>
      </router-link>
    </div>
  </div>
</template>
