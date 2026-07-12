<script setup lang="ts">
import PostEditorPopoverActions from './PostEditorPopoverActions.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
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
    <BaseInput
      id="editor-table-rows"
      :model-value="rows"
      type="number"
      name="tableRows"
      inputmode="numeric"
      autocomplete="off"
      min="1"
      max="20"
      :label="t('board.writePost.tableRows')"
      label-class="link-popover-label"
      input-class="link-popover-input"
      @update:model-value="emit('update:rows', Number($event))"
      @keydown.enter.stop.prevent="emit('apply')"
      @keydown.escape.stop.prevent="emit('close')"
    />
  </div>
  <div class="link-popover-row">
    <BaseInput
      id="editor-table-cols"
      :model-value="cols"
      type="number"
      name="tableCols"
      inputmode="numeric"
      autocomplete="off"
      min="1"
      max="10"
      :label="t('board.writePost.tableCols')"
      label-class="link-popover-label"
      input-class="link-popover-input"
      @update:model-value="emit('update:cols', Number($event))"
      @keydown.enter.stop.prevent="emit('apply')"
      @keydown.escape.stop.prevent="emit('close')"
    />
  </div>
  <div class="link-popover-row">
    <BaseCheckbox
      id="table-header-row"
      :model-value="headerRow"
      name="tableHeaderRow"
      :label="t('board.writePost.tableHeaderRow')"
      @update:model-value="emit('update:header-row', Boolean($event))"
    />
  </div>
  <PostEditorPopoverActions
    :cancel-label="t('common.cancel')"
    :apply-label="t('board.writePost.tableInsert')"
    @close="emit('close')"
    @apply="emit('apply')"
  />
</template>
