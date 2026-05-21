<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
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
      class="rounded border-[var(--nv-line)]"
      @change="emit('update:header-row', ($event.target as HTMLInputElement).checked)"
    >
    <label for="table-header-row" class="link-popover-label !mb-0">{{ t('board.writePost.tableHeaderRow') }}</label>
  </div>
  <div class="link-popover-actions">
    <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
      {{ t('common.cancel') }}
    </BaseButton>
    <BaseButton type="button" variant="primary" size="sm" @click="emit('apply')">
      {{ t('board.writePost.tableInsert') }}
    </BaseButton>
  </div>
</template>
