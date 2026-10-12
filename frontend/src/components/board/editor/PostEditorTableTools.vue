<script setup lang="ts">
import type { Editor } from '@tiptap/core'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

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
