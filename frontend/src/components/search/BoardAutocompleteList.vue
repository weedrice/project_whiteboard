<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { BoardListItem } from '@/types'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'

defineProps<{
  boards: BoardListItem[]
  listboxId: string
  selectedIndex: number
  searchQuery: string
}>()

const emit = defineEmits<{
  (e: 'select', boardUrl: string): void
  (e: 'highlight', index: number): void
  (e: 'search'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="boards.length > 0">
    <div class="nv-global-search-section nv-global-search-section-text px-3 py-2 text-xs font-semibold">
      {{ t('search.boards') }}
    </div>
    <ul :id="listboxId" role="listbox" :aria-label="t('search.boardResultsLabel')">
      <li
        v-for="(board, index) in boards"
        :id="`${listboxId}-${board.boardUrl}`"
        :key="board.boardUrl"
        :class="[
          'nv-global-search-option px-4 py-2 cursor-pointer flex items-center space-x-3',
          { 'nv-global-search-option-selected': index === selectedIndex }
        ]"
        :aria-selected="index === selectedIndex"
        role="option"
        tabindex="-1"
        @click="emit('select', board.boardUrl)"
        @mouseenter="emit('highlight', index)"
      >
        <div class="nv-global-search-badge flex-shrink-0 h-8 w-8 rounded flex items-center justify-center font-bold overflow-hidden border">
          <img
            v-if="board.iconUrl"
            :src="getOptimizedBoardIconUrl(board.iconUrl)"
            loading="lazy"
            decoding="async"
            class="nv-global-search-board-icon h-full w-full object-contain"
            alt=""
            @error="handleImageError($event)"
          />
          <span v-else class="text-xs">{{ board.boardName.substring(0, 1) }}</span>
        </div>
        <span class="nv-global-search-board-name text-sm font-medium">{{ board.boardName }}</span>
      </li>
    </ul>
  </div>
  <button
    type="button"
    class="nv-global-search-action border-t px-4 py-3 flex w-full items-center text-left"
    @click="emit('search')"
  >
    <Search class="h-4 w-4 mr-2" />
    <span class="text-sm font-medium">{{ t('search.doSearch', { query: searchQuery }) }}</span>
  </button>
</template>

<style scoped>
.nv-global-search-board-icon {
  background: color-mix(in srgb, var(--nv-surface) 98%, transparent);
}

.nv-global-search-badge {
  background: color-mix(in srgb, var(--nv-accent-bg) 92%, transparent);
  border-color: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-global-search-board-name {
  color: var(--nv-ink);
}

.nv-global-search-section {
  background: color-mix(in srgb, var(--nv-surface-2) 78%, transparent);
}

.nv-global-search-section-text {
  color: var(--nv-muted);
}

.nv-global-search-action {
  border-color: var(--nv-line);
  color: var(--nv-accent);
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-global-search-action:hover {
  background: color-mix(in srgb, var(--nv-surface-2) 82%, transparent);
}

.nv-global-search-option {
  transition: background-color 0.2s ease;
}

.nv-global-search-option:hover,
.nv-global-search-option-selected {
  background: color-mix(in srgb, var(--nv-surface-2) 84%, transparent);
}
</style>
