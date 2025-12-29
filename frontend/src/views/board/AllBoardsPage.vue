<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/composables/useBoard'
import SubscribedBoardList from '@/components/board/SubscribedBoardList.vue'
import BoardGrid from '@/components/board/BoardGrid.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

const authStore = useAuthStore()
const { useBoards } = useBoard()
const { data: boards, isLoading, error } = useBoards()

const subscribedBoards = computed(() => {
  return boards.value?.filter(board => board.isSubscribed) || []
})

const allBoards = computed(() => {
  return boards.value || []
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">

    <div v-if="isLoading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
      <div v-for="i in 6" :key="i" class="bg-white dark:bg-gray-800 rounded-lg shadow p-6 flex items-center space-x-4">
        <BaseSkeleton width="3rem" height="3rem" rounded="rounded-full" />
        <div class="flex-1">
          <BaseSkeleton width="60%" height="24px" className="mb-2" />
          <BaseSkeleton width="90%" height="16px" />
        </div>
      </div>
    </div>

    <div v-else-if="error" class="text-center py-20 text-red-500 dark:text-red-400">
      {{ error }}
    </div>

    <div v-else>
      <!-- Subscribed Boards -->
      <SubscribedBoardList v-if="authStore.isAuthenticated && subscribedBoards.length > 0" :boards="subscribedBoards" />

      <!-- All Boards -->
      <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6"
        v-if="authStore.isAuthenticated && subscribedBoards.length > 0">{{ $t('board.list.title') }}</h2>
      <BoardGrid :boards="allBoards" />
    </div>
  </div>
</template>
