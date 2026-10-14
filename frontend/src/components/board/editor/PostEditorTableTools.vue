<script setup lang="ts">
import type { Editor } from '@tiptap/core'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { distributePostEditorColumns } from '@/features/board/posts/editor/postEditorTableFormatting'

const props = defineProps<{ editor: Editor }>()
const { t } = useI18n()

const isTableActive = computed(() => props.editor.isActive('table'))
const hasTable = computed(() => {
  let found = false
  props.editor.state.doc.descendants((node) => {
    if (node.type.name === 'table') found = true
    return !found
  })
  return found
})
const backgroundColors = ['#fef3c7', '#dbeafe', '#dcfce7', '#fce7f3', '#e5e7eb']
const colorLabels = computed(() => [
  t('board.writePost.colorLabels.yellow'), t('board.writePost.colorLabels.blue'),
  t('board.writePost.colorLabels.green'), t('board.writePost.colorLabels.pink'),
  t('board.writePost.colorLabels.gray'),
])
const cellBackground = computed(() => {
  const cells: string[] = []
  const selection = props.editor.state.selection
  if ('forEachCell' in selection && typeof selection.forEachCell === 'function') {
    selection.forEachCell((node: { attrs: Record<string, unknown> }) => cells.push(String(node.attrs.backgroundColor ?? '')))
  } else {
    cells.push(props.editor.getAttributes('tableCell').backgroundColor ?? props.editor.getAttributes('tableHeader').backgroundColor ?? '')
  }
  const color = cells.every(value => value === cells[0]) ? cells[0] : 'mixed'
  // HTML parsers normalize hex colors to rgb(); normalize the presets too.
  return backgroundColors.find(value => {
    const element = document.createElement('span')
    element.style.backgroundColor = value
    return value === color || element.style.backgroundColor === color
  }) ?? color
})

function setBackground(value: string) {
  if (!props.editor.isEditable || !isTableActive.value) return
  if (value && !backgroundColors.includes(value)) return
  props.editor.chain().focus().setCellAttribute('backgroundColor', value || null).run()
}

const actionGroups = [
  ['addRowBefore', 'addRowAfter', 'deleteRow'],
  ['addColumnBefore', 'addColumnAfter', 'deleteColumn'],
  ['mergeCells', 'splitCell', 'toggleHeaderRow', 'toggleHeaderColumn'],
  ['deleteTable'],
] as const

type TableAction = typeof actionGroups[number][number]

function runAction(action: TableAction) {
  if (!props.editor.isEditable || !props.editor.isActive('table')) return
  props.editor.chain().focus()[action]().run()
}
</script>

<template>
  <div
    v-if="hasTable"
    class="tiptap-table-tools"
    :class="{ 'tiptap-table-tools--inactive': !isTableActive }"
    :inert="isTableActive ? undefined : true"
    :aria-hidden="!isTableActive"
    role="group"
    :aria-label="t('board.writePost.tableTools.label')"
  >
    <p class="tiptap-toolbar-context">{{ t('board.writePost.tableTools.hint') }}</p>
    <div class="tiptap-toolbar-row tiptap-toolbar-row--scrollable">
      <div class="tiptap-toolbar-group">
        <button type="button" class="tiptap-btn tiptap-btn-pill" data-table-action="distributeColumns" :disabled="!isTableActive || !editor.isEditable" @mousedown.prevent @click="distributePostEditorColumns(editor)">{{ t('board.writePost.tableTools.distributeColumns') }}</button>
        <select data-ui-native="specialized" class="tiptap-select text-xs" :value="cellBackground" :disabled="!isTableActive || !editor.isEditable" :aria-label="t('board.writePost.tableTools.background')" @change="setBackground(($event.target as HTMLSelectElement).value)">
          <option value="">{{ t('board.writePost.tableTools.noBackground') }}</option>
          <option v-for="(color, index) in backgroundColors" :key="color" :value="color">{{ colorLabels[index] }}</option>
          <option v-if="cellBackground && !backgroundColors.includes(cellBackground)" :value="cellBackground" disabled>{{ t('board.writePost.tableTools.customBackground') }}</option>
        </select>
      </div>
      <div v-for="(actions, index) in actionGroups" :key="index" class="tiptap-toolbar-group">
        <button
          v-for="action in actions"
          :key="action"
          type="button"
          class="tiptap-btn tiptap-btn-pill"
          :data-table-action="action"
          :disabled="!isTableActive || !editor.isEditable || !editor.can()[action]()"
          @mousedown.prevent
          @click="runAction(action)"
        >
          {{ t(`board.writePost.tableTools.${action}`) }}
        </button>
      </div>
    </div>
  </div>
</template>
