<script setup lang="ts">
import BoardCard from './BoardCard.vue'
import type { Board } from '@/api/board'

defineProps<{
  boards: Board[]
}>()
</script>

<template>
  <div>
    <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <BoardCard
        v-for="board in boards"
        :key="board.boardUrl"
        :board="board"
      />
    </div>
    
    <div v-if="boards.length === 0" class="text-center py-20 text-gray-500 dark:text-gray-400">
      {{ $t('board.list.empty') }}
    </div>
  </div>
</template>
