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
    <div class="mb-6">
      <h2 class="text-2xl font-bold text-gray-900">Boards</h2>
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
        :key="board.boardUrl"
        :to="`/board/${board.boardUrl}`"
        class="relative rounded-lg border border-gray-300 bg-white px-6 py-5 shadow-sm hover:border-gray-400 focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-indigo-500 block"
      >
        <div class="flex justify-between items-center mb-2">
          <p class="text-base font-bold text-gray-900 truncate mr-2">
            {{ board.boardName }}
          </p>
          <div class="flex-shrink-0 text-gray-400 text-xs flex items-center">
            <Users class="h-3 w-3 mr-1" />
            {{ board.subscriberCount }}
          </div>
        </div>
        <div class="mt-3 text-sm text-gray-600 space-y-1">
          <p v-if="board.latestPosts && board.latestPosts.length === 0" class="text-gray-500">
            아직 게시글이 없습니다.
          </p>
          <router-link
            v-for="post in board.latestPosts"
            :key="post.postId"
            :to="`/board/${board.boardUrl}/post/${post.postId}`"
            class="block hover:underline text-gray-700 truncate"
          >
            {{ post.title }}
          </router-link>
        </div>
      </router-link>
    </div>
  </div>
</template>
