<script setup lang="ts">
import type { Board } from '@/api/board'

defineProps<{
  boards: Board[]
}>()
</script>

<template>
  <div class="mb-12">
    <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">{{ $t('board.list.subscribed') }}</h2>
    <div class="flex space-x-6 overflow-x-auto pb-4">
      <router-link
        v-for="board in boards"
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
</template>
