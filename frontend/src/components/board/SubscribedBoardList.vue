<script setup lang="ts">
import type { BoardListItem } from '@/types'

defineProps<{
  boards: BoardListItem[]
}>()
</script>

<template>
  <div class="mb-12">
    <h2 class="text-xl font-bold nv-title mb-6">{{ $t('board.list.subscribed') }}</h2>
    <div class="flex space-x-6 overflow-x-auto p-2 pb-4">
      <router-link v-for="board in boards" :key="board.boardUrl"
        v-memo="[board.boardUrl, board.boardName, board.iconUrl]" :to="`/board/${board.boardUrl}`"
        class="flex flex-col items-center group flex-shrink-0 min-w-[80px]">
        <div
          class="h-16 w-16 rounded-full nv-avatar-fallback flex items-center justify-center group-hover:ring-2 ring-[var(--nv-focus)] transition-all overflow-hidden border shadow-sm">
          <img v-if="board.iconUrl" :src="board.iconUrl" class="h-full w-full object-contain nv-surface-muted"
            alt="" />
          <span v-else class="text-indigo-600 dark:text-indigo-400 font-bold text-2xl">{{ board.boardName[0] }}</span>
        </div>
        <span
          class="mt-3 text-sm font-medium nv-text-muted group-hover:text-indigo-600 dark:group-hover:text-indigo-400 text-center truncate w-full">{{
            board.boardName }}</span>
      </router-link>
    </div>
  </div>
</template>
