<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useBoard } from '@/composables/useBoard'
import SubscribedBoardList from '@/components/board/SubscribedBoardList.vue'
import BoardGrid from '@/components/board/BoardGrid.vue'
import BoardListSkeleton from '@/components/common/ui/BoardListSkeleton.vue'

import { useHead } from '@unhead/vue'

const authStore = useAuthStore()
const { useBoards } = useBoard()
const { data: boards, isLoading, error } = useBoards()

useHead({
  title: 'All Boards',
  meta: [
    { name: 'description', content: '노비스에서 모든 게시판과 커뮤니티를 둘러보세요.' },
    { property: 'og:title', content: '전체 게시판 | 노비스' },
    { property: 'og:description', content: '노비스에서 모든 게시판과 커뮤니티를 둘러보세요.' }
  ]
})

const subscribedBoards = computed(() => {
  return boards.value?.filter(board => board.isSubscribed) || []
})

const allBoards = computed(() => {
  return boards.value || []
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">

    <BoardListSkeleton v-if="isLoading" :count="6" />

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
