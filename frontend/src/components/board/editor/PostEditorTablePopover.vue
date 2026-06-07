<script setup lang="ts">
import PostEditorPopoverActions from './PostEditorPopoverActions.vue'
import { useI18n } from 'vue-i18n'

defineProps<{
  rows: number
  cols: number
  headerRow: boolean
}>()

const emit = defineEmits<{
  (e: 'update:rows', value: number): void
  (e: 'update:cols', value: number): void
  (e: 'update:header-row', value: boolean): void
  (e: 'apply'): void
  (e: 'close'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="link-popover-row">
    <label for="editor-table-rows" class="link-popover-label">{{ t('board.writePost.tableRows') }}</label>
    <input
      id="editor-table-rows"
      :value="rows"
      type="number"
      name="tableRows"
      inputmode="numeric"
      autocomplete="off"
      min="1"
      max="20"
      class="link-popover-input"
      @input="emit('update:rows', Number(($event.target as HTMLInputElement).value))"
      @keydown.enter.stop.prevent="emit('apply')"
      @keydown.escape.stop.prevent="emit('close')"
    >
  </div>
  <div class="link-popover-row">
    <label for="editor-table-cols" class="link-popover-label">{{ t('board.writePost.tableCols') }}</label>
    <input
      id="editor-table-cols"
      :value="cols"
      type="number"
      name="tableCols"
      inputmode="numeric"
      autocomplete="off"
      min="1"
      max="10"
      class="link-popover-input"
      @input="emit('update:cols', Number(($event.target as HTMLInputElement).value))"
      @keydown.enter.stop.prevent="emit('apply')"
      @keydown.escape.stop.prevent="emit('close')"
    >
  </div>
  <div class="link-popover-row flex items-center gap-2">
    <input
      id="table-header-row"
      :checked="headerRow"
      type="checkbox"
      name="tableHeaderRow"
      class="rounded border-[var(--nv-line)]"
      @change="emit('update:header-row', ($event.target as HTMLInputElement).checked)"
    >
    <label for="table-header-row" class="link-popover-label !mb-0">{{ t('board.writePost.tableHeaderRow') }}</label>
  </div>
  <PostEditorPopoverActions
    :cancel-label="t('common.cancel')"
    :apply-label="t('board.writePost.tableInsert')"
    @close="emit('close')"
    @apply="emit('apply')"
  />
</template>
