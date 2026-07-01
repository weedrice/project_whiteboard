<template>
  <AdminPanel padding="sm">
    <h2 class="text-sm font-semibold nv-title">{{ t('common.board') }}</h2>

    <div v-if="loading" class="py-8 flex justify-center">
      <BaseSpinner />
    </div>

    <div v-else-if="boards.length === 0" class="py-8 text-sm nv-text-subtle text-center">
      {{ t('common.noData') }}
    </div>

    <draggable
      v-else
      :model-value="boards"
      item-key="boardId"
      handle=".drag-handle"
      class="mt-3 space-y-2 max-h-[70vh] overflow-y-auto pr-1"
      @update:model-value="emit('update:boards', $event)"
      @end="emit('drag-end')"
    >
      <template #item="{ element: board }">
        <button
          type="button"
          class="w-full rounded-lg border px-3 py-3 text-left transition-colors"
          :class="selectedBoardId === board.boardId ? 'board-row-selected' : 'nv-border nv-hover-surface'"
          @click="emit('select', board)"
        >
          <div class="flex items-start justify-between gap-2">
            <p class="truncate text-sm font-semibold nv-title flex items-center gap-2">
              <GripVertical class="h-4 w-4 nv-text-subtle drag-handle cursor-move" />
              {{ board.boardName }}
            </p>
            <BooleanBadge
              :value="board.isActive"
              :true-label="t('common.active')"
              :false-label="t('common.inactive')"
              false-variant="danger"
            />
          </div>
          <div class="mt-2 text-xs nv-text-subtle">
            {{ t('common.sortOrder') }}: {{ board.sortOrder }}
          </div>
        </button>
      </template>
    </draggable>
  </AdminPanel>
</template>

<script setup lang="ts">
import draggable from 'vuedraggable'
import { GripVertical } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { AdminBoard } from '@/types'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

defineProps<{
  boards: AdminBoard[]
  loading: boolean
  selectedBoardId: number | null
}>()

const emit = defineEmits<{
  (event: 'update:boards', boards: AdminBoard[]): void
  (event: 'select', board: AdminBoard): void
  (event: 'drag-end'): void
}>()

const { t } = useI18n()
</script>

<style scoped>
.board-row-selected {
  background: var(--nv-selection);
  border-color: var(--nv-focus);
}
</style>
