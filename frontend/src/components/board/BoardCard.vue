<script setup lang="ts">
import { Users, User } from 'lucide-vue-next'
import type { Board } from '@/api/board'

defineProps<{
  board: Board
}>()
</script>

<template>
  <router-link
    :to="`/board/${board.boardUrl}`"
    class="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg hover:shadow-md transition-all duration-200 border border-gray-100 dark:border-gray-700 block"
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
</template>
