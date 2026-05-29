<script setup lang="ts">
import { Users, User } from 'lucide-vue-next'
import type { BoardListItem } from '@/types'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'

defineProps<{
  board: BoardListItem
}>()
</script>

<template>
  <router-link
    :to="`/board/${board.boardUrl}`"
    class="nv-surface overflow-hidden shadow rounded-lg hover:shadow-md transition-all duration-200 border nv-border block"
  >
    <div class="p-6">
      <div class="flex items-center">
        <div class="flex-shrink-0 h-12 w-12 rounded-md nv-avatar-fallback flex items-center justify-center text-xl font-bold overflow-hidden border">
          <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)" class="h-full w-full object-contain nv-surface-muted" alt=""
            @error="handleImageError($event)" />
          <span v-else>{{ board.boardName.substring(0, 1) }}</span>
        </div>
        <div class="ml-4">
          <h3 class="text-lg font-medium nv-title">{{ board.boardName }}</h3>
          <div class="flex items-center mt-1 text-sm nv-text-subtle">
            <User class="flex-shrink-0 mr-1.5 h-4 w-4" />
            <span>{{ board.adminDisplayName || $t('common.admin') }}</span>
          </div>
        </div>
      </div>
      <div class="mt-4">
        <p class="text-sm nv-text-subtle line-clamp-2 h-10">
          {{ board.description || $t('board.list.noDesc') }}
        </p>
      </div>
      <div class="mt-4 pt-4 border-t nv-border flex items-center justify-between">
         <div class="flex items-center text-sm nv-text-subtle">
            <Users class="flex-shrink-0 mr-1.5 h-4 w-4" />
            <span>{{ $t('board.list.subscribers', { count: board.subscriberCount || 0 }) }}</span>
         </div>
      </div>
    </div>
  </router-link>
</template>
