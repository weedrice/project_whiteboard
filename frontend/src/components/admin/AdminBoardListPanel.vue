<template>
  <AdminPanel padding="sm">
    <h2 class="text-sm font-semibold nv-title">{{ t('common.board') }}</h2>

    <AdminContentState :loading="loading" :empty="boards.length === 0" :empty-text="t('common.noData')" :action-label="t('admin.boards.addTitle')" @action="emit('create')">
      <draggable
        :model-value="boards"
        item-key="boardId"
        handle=".drag-handle"
        :disabled="reorderDisabled"
        class="mt-3 space-y-2 max-h-[70vh] overflow-y-auto pr-1"
        @update:model-value="handleBoardsUpdate"
        @end="handleDragEnd"
      >
        <template #item="{ element: board, index }">
          <div
            class="w-full rounded-lg border px-3 py-3 text-left transition-colors"
            :class="selectedBoardId === board.boardId ? 'board-row-selected' : 'nv-border nv-hover-surface'"
          >
            <div class="flex items-start justify-between gap-2">
              <GripVertical
                class="mt-0.5 h-4 w-4 shrink-0 nv-text-subtle drag-handle"
                :class="reorderDisabled ? 'cursor-not-allowed opacity-40' : 'cursor-move'"
                aria-hidden="true"
              />
              <button type="button" class="min-h-11 min-w-0 flex-1 rounded-md text-left nv-focus-ring" @click="emit('select', board)">
                <span class="block truncate text-sm font-semibold nv-title">{{ board.boardName }}</span>
                <span class="mt-2 block text-xs nv-text-subtle">
                  {{ t('common.sortOrder') }}: {{ board.sortOrder }}
                </span>
              </button>
              <BooleanBadge
                :value="board.isActive"
                :true-label="t('common.active')"
                :false-label="t('common.inactive')"
                false-variant="danger"
              />
              <div class="flex shrink-0 items-center gap-1">
                <button
                  type="button"
                  class="nv-touch-target-square nv-focus-ring inline-flex items-center justify-center rounded-md nv-text-subtle nv-hover-surface disabled:cursor-not-allowed disabled:opacity-40"
                  :aria-label="t('common.moveUp', { name: board.boardName })"
                  :disabled="reorderDisabled || index === 0"
                  @click="moveBoard(index, -1)"
                >
                  <ChevronUp class="h-4 w-4" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  class="nv-touch-target-square nv-focus-ring inline-flex items-center justify-center rounded-md nv-text-subtle nv-hover-surface disabled:cursor-not-allowed disabled:opacity-40"
                  :aria-label="t('common.moveDown', { name: board.boardName })"
                  :disabled="reorderDisabled || index === boards.length - 1"
                  @click="moveBoard(index, 1)"
                >
                  <ChevronDown class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </template>
      </draggable>
      <p class="sr-only" role="status" aria-live="polite">{{ reorderAnnouncement }}</p>
    </AdminContentState>
  </AdminPanel>
</template>

<script setup lang="ts">
import draggable from 'vuedraggable'
import { ref } from 'vue'
import { ChevronDown, ChevronUp, GripVertical } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { AdminBoard } from '@/types'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminContentState from '@/components/admin/AdminContentState.vue'
import BooleanBadge from '@/components/admin/BooleanBadge.vue'

const props = defineProps<{
  boards: AdminBoard[]
  loading: boolean
  selectedBoardId: number | null
  reorderDisabled?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:boards', boards: AdminBoard[]): void
  (event: 'select', board: AdminBoard): void
  (event: 'drag-end'): void
  (event: 'create'): void
}>()

const { t } = useI18n()
const reorderAnnouncement = ref('')

const handleBoardsUpdate = (boards: AdminBoard[]) => {
  if (props.reorderDisabled) return
  emit('update:boards', boards)
}

const handleDragEnd = () => {
  if (props.reorderDisabled) return
  emit('drag-end')
}

const moveBoard = (index: number, offset: -1 | 1) => {
  if (props.reorderDisabled) return
  const targetIndex = index + offset
  if (targetIndex < 0 || targetIndex >= props.boards.length) return

  const nextBoards = [...props.boards]
  const [movedBoard] = nextBoards.splice(index, 1)
  if (!movedBoard) return
  nextBoards.splice(targetIndex, 0, movedBoard)
  emit('update:boards', nextBoards)
  emit('drag-end')
  reorderAnnouncement.value = t('common.moved', {
    name: movedBoard.boardName,
    position: targetIndex + 1,
  })
}
</script>

<style scoped>
.board-row-selected {
  background: var(--nv-selection);
  border-color: var(--nv-focus);
}
</style>
