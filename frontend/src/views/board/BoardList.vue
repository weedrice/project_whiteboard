<script setup>
import { ref, onMounted, computed } from 'vue'
import { boardApi } from '@/api/board'
import { Users, PlusCircle } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const boards = ref([])
const isLoading = ref(true)
const error = ref('')

const subscribedBoards = computed(() => {
  return boards.value.filter(board => board.isSubscribed)
})

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
    <!-- Banner Area -->
    <div class="w-[80%] mx-auto bg-gray-200 h-[120px] mb-8 rounded-lg flex items-center justify-center text-gray-500 text-xl font-medium">
      상단 배너 영역 (80% Width)
    </div>

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else>
      <!-- Subscribed Boards -->
      <div v-if="authStore.isAuthenticated && subscribedBoards.length > 0" class="mb-8">
        <h3 class="text-lg font-bold text-gray-900 mb-4 px-4">구독한 게시판</h3>
        <div class="flex space-x-6 overflow-x-auto px-4 pb-4">
          <router-link
            v-for="board in subscribedBoards"
            :key="board.boardUrl"
            :to="`/board/${board.boardUrl}`"
            class="flex flex-col items-center group flex-shrink-0"
          >
            <div class="h-16 w-16 rounded-full bg-indigo-100 flex items-center justify-center group-hover:ring-2 ring-indigo-500 transition-all overflow-hidden">
               <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-cover" alt="" />
               <span v-else class="text-indigo-600 font-bold text-xl">{{ board.boardName[0] }}</span>
            </div>
            <span class="mt-2 text-xs font-medium text-gray-700 group-hover:text-indigo-600">{{ board.boardName }}</span>
          </router-link>
        </div>
      </div>

      <!-- All Boards -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
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
  </div>
</template>
