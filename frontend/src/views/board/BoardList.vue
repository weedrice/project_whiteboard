<script setup>
import { computed } from 'vue'
import { useBoard } from '@/composables/useBoard'
import { Users } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const { useBoards } = useBoard()
const { data: boards, isLoading, error } = useBoards()

const subscribedBoards = computed(() => {
  return boards.value?.filter(board => board.isSubscribed) || []
})
</script>

<template>
  <div>
    <!-- Banner Area (Hidden) -->
    <!-- <div class="w-[90%] mx-auto mb-8">
      <AdBanner placement="HEADER" />
    </div> -->

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500 dark:text-red-400">
      {{ error }}
    </div>

    <div v-else>
      <!-- Subscribed Boards -->
      <div v-if="authStore.isAuthenticated && subscribedBoards.length > 0" class="mb-8">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-4 px-4">{{ $t('board.list.subscribed') }}</h3>
        <div class="flex space-x-6 overflow-x-auto px-4 pb-4">
          <router-link
            v-for="board in subscribedBoards"
            :key="board.boardUrl"
            :to="`/board/${board.boardUrl}`"
            class="flex flex-col items-center group flex-shrink-0"
          >
            <div class="h-16 w-16 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center group-hover:ring-2 ring-indigo-500 transition-all overflow-hidden border border-gray-200 dark:border-gray-700">
               <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-contain bg-white dark:bg-gray-800" alt="" />
               <span v-else class="text-indigo-600 dark:text-indigo-400 font-bold text-xl">{{ board.boardName[0] }}</span>
            </div>
            <span class="mt-2 text-xs font-medium text-gray-700 dark:text-gray-300 group-hover:text-indigo-600 dark:group-hover:text-indigo-400">{{ board.boardName }}</span>
          </router-link>
        </div>
      </div>

      <!-- All Boards -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <router-link
          v-for="board in boards"
          :key="board.boardUrl"
          :to="`/board/${board.boardUrl}`"
          class="relative rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-6 py-5 shadow-sm hover:border-gray-400 dark:hover:border-gray-600 focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-indigo-500 block transition-colors duration-200"
        >
          <div class="flex justify-between items-center mb-2">
            <p class="text-xl font-bold text-gray-900 dark:text-white truncate mr-2">
              {{ board.boardName }}
            </p>
            <div class="flex-shrink-0 text-gray-400 dark:text-gray-500 text-xs flex items-center">
              <Users class="h-3 w-3 mr-1" />
              {{ board.subscriberCount }}
            </div>
          </div>
          <div class="mt-3 text-sm text-gray-600 dark:text-gray-400 space-y-3">
            <p v-if="board.latestPosts && board.latestPosts.length === 0" class="text-gray-500 dark:text-gray-500">
              {{ $t('board.list.noPosts') }}
            </p>
            <router-link
              v-for="post in board.latestPosts.slice(0, 10)"
              :key="post.postId"
              :to="`/board/${board.boardUrl}/post/${post.postId}`"
              class="block hover:underline text-gray-700 dark:text-gray-300 hover:text-indigo-600 dark:hover:text-indigo-400 truncate"
            >
              {{ post.title }} <span class="text-gray-500 dark:text-gray-500 text-xs">[{{ post.commentCount || 0 }}]</span>
            </router-link>
          </div>
        </router-link>
      </div>
    </div>
  </div>
</template>
