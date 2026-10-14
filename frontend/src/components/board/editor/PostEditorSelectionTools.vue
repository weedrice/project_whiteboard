<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { TextSelection } from '@tiptap/pm/state'
import { useI18n } from 'vue-i18n'
const props = defineProps<{ editor: Editor; focused?: boolean }>()
const emit = defineEmits<{ (event: 'link', anchor: HTMLElement): void; (event: 'color', anchor: HTMLElement): void }>()
const { t } = useI18n()
const menu = ref<HTMLElement | null>(null)
const visible = ref(false)
const position = ref({ left: '0px', top: '0px' })
function update() {
  const editor = props.editor
  if (editor.isDestroyed) { visible.value = false; return }
  const { selection, doc } = editor.state
  const menuFocused = menu.value?.contains(document.activeElement)
  visible.value = editor.isEditable && (editor.isFocused || !!menuFocused)
    && selection instanceof TextSelection && !selection.empty && !editor.isActive('codeBlock')
  if (!visible.value) return
  let protectedBlock = false
  doc.nodesBetween(selection.from, selection.to, node => {
    if (node.type.name === 'rawHtmlBlock' || node.type.name === 'codeBlock') protectedBlock = true
  })
  if (protectedBlock) { visible.value = false; return }
  try {
    const rect = editor.view.coordsAtPos(selection.from)
    const editorRect = editor.view.dom.getBoundingClientRect()
    const scrollRect = editor.view.dom.closest('.tiptap-content')?.getBoundingClientRect() ?? editorRect
    if (rect.top < Math.max(0, editorRect.top, scrollRect.top) || rect.bottom > Math.min(window.innerHeight, editorRect.bottom, scrollRect.bottom)) {
      visible.value = false
      return
    }
    position.value = { left: `${Math.max(8, Math.min(rect.left, window.innerWidth - 248))}px`, top: `${Math.max(8, rect.top - 44)}px` }
  } catch { visible.value = false }
}
onMounted(() => {
  props.editor.on('selectionUpdate', update)
  props.editor.on('transaction', update)
  props.editor.on('focus', update)
  props.editor.on('blur', update)
  window.addEventListener('scroll', update, true)
  window.addEventListener('resize', update)
})
onBeforeUnmount(() => {
  props.editor.off('selectionUpdate', update)
  props.editor.off('transaction', update)
  props.editor.off('focus', update)
  props.editor.off('blur', update)
  window.removeEventListener('scroll', update, true)
  window.removeEventListener('resize', update)
})
</script>
<template>
  <Teleport to="body" :disabled="focused">
    <div ref="menu" class="post-editor-selection-tools" :style="[position, { visibility: visible ? 'visible' : 'hidden' }]" :inert="visible ? undefined : true" :aria-hidden="!visible" role="group" :aria-label="t('board.writePost.advanced.selectionTools')" @mousedown.prevent @keydown.esc.stop="visible = false; editor.commands.focus()">
      <button type="button" class="tiptap-btn" :aria-label="t('board.writePost.toolbar.bold')" :aria-pressed="editor.isActive('bold')" @click="editor.chain().focus().toggleBold().run()"><strong>B</strong></button>
      <button type="button" class="tiptap-btn" :aria-label="t('board.writePost.toolbar.italic')" :aria-pressed="editor.isActive('italic')" @click="editor.chain().focus().toggleItalic().run()"><em>I</em></button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" @click="emit('link', $event.currentTarget as HTMLElement); visible = false">{{ t('board.writePost.advanced.link') }}</button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" @click="emit('color', $event.currentTarget as HTMLElement); visible = false">{{ t('board.writePost.advanced.color') }}</button>
    </div>
  </Teleport>
</template>
