<script setup lang="ts">
import { SeparatorHorizontal, Table2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

defineProps<{
  showTablePopover: boolean
}>()

const emit = defineEmits<{
  (e: 'open-table', trigger: HTMLElement): void
  (e: 'horizontal-rule'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="grid gap-3">
    <div class="flex flex-wrap items-center gap-2">
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.tableDialog')" :aria-label="t('board.writePost.toolbar.tableDialog')" aria-haspopup="dialog" :aria-expanded="showTablePopover" aria-controls="editor-table-dialog" @mousedown.prevent @click="emit('open-table', $event.currentTarget as HTMLElement)">
        <Table2 :size="16" aria-hidden="true" />
      </button>
      <button type="button" class="tiptap-btn" :title="t('board.writePost.toolbar.divider')" :aria-label="t('board.writePost.toolbar.divider')" @mousedown.prevent @click="emit('horizontal-rule')">
        <SeparatorHorizontal :size="16" aria-hidden="true" />
      </button>
    </div>

  </div>
</template>
