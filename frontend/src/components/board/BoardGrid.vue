<script setup lang="ts">
import { LayoutGrid } from 'lucide-vue-next'
import BoardCard from './BoardCard.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import type { BoardListItem } from '@/types'

defineProps<{
  boards: BoardListItem[]
}>()
</script>

<template>
  <div>
    <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <BoardCard
        v-for="board in boards"
        :key="board.boardUrl"
        v-memo="[board.boardUrl, board.boardName, board.description, board.adminDisplayName, board.iconUrl, board.isSubscribed, board.subscriberCount]"
        :board="board"
      />
    </div>
    
    <EmptyState
      v-if="boards.length === 0"
      title-tag="h2"
      :title="$t('board.list.empty')"
      :icon="LayoutGrid"
      container-class="py-20"
    />
  </div>
</template>
