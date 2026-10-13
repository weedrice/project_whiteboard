<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { exitPostEditorContentBlock, findPostEditorText, replacePostEditorText } from '@/features/board/posts/editor/postEditorDocumentTools'
import type { PostEditorPasteMode } from '@/features/board/posts/editor/postEditorPaste'
import { isComposingKeyboardEvent } from '@/utils/keyboard'

const props = defineProps<{ editor: Editor; pasteMode: PostEditorPasteMode; focused: boolean }>()
const emit = defineEmits<{
  (event: 'update:pasteMode', mode: PostEditorPasteMode): void
  (event: 'update:focused', focused: boolean): void
}>()
const { t } = useI18n()
const panel = ref<'outline' | 'find' | null>(null)
const query = ref('')
const replacement = ref('')
function closePanel(event: KeyboardEvent) {
  if (isComposingKeyboardEvent(event)) return
  panel.value = null
  props.editor.commands.focus()
}
function findNext(event: KeyboardEvent) {
  if (isComposingKeyboardEvent(event)) return
  event.preventDefault()
  navigate(1)
}
const matches = computed(() => findPostEditorText(props.editor.state.doc, query.value))
const selectedMatch = computed(() => matches.value.findIndex(match => match.from === props.editor.state.selection.from && match.to === props.editor.state.selection.to))
const outline = computed(() => {
  const headings: { position: number; title: string; level: number }[] = []
  props.editor.state.doc.descendants((node, position) => {
    if (node.type.name === 'heading') headings.push({ position, title: node.textContent, level: node.attrs.level })
  })
  return headings
})
function navigate(direction: 1 | -1) {
  if (!matches.value.length) return
  const index = selectedMatch.value < 0 ? (direction === 1 ? 0 : matches.value.length - 1)
    : (selectedMatch.value + direction + matches.value.length) % matches.value.length
  props.editor.chain().setTextSelection(matches.value[index]).scrollIntoView().run()
}
function jump(position: number) {
  // A heading inside a collapsed section must be visible before focusing it.
  let element = props.editor.view.nodeDOM(position) as HTMLElement | null
  while (element) {
    if (element instanceof HTMLDetailsElement) element.open = true
    element = element.parentElement
  }
  props.editor.chain().focus().setTextSelection(position + 1).scrollIntoView().run()
}
</script>
<template>
  <div class="post-editor-document-tools">
    <div class="tiptap-toolbar-row tiptap-toolbar-row--scrollable">
      <select data-ui-native="specialized" class="tiptap-select" :aria-label="t('board.writePost.advanced.pasteMode')" :value="pasteMode" @change="emit('update:pasteMode', ($event.target as HTMLSelectElement).value as PostEditorPasteMode)">
        <option value="original">{{ t('board.writePost.advanced.pasteOriginal') }}</option>
        <option value="clean">{{ t('board.writePost.advanced.pasteClean') }}</option>
        <option value="text">{{ t('board.writePost.advanced.pasteText') }}</option>
      </select>
      <button v-if="editor.isActive('callout') || editor.isActive('details') || editor.isActive('taskList')" type="button" class="tiptap-btn tiptap-btn-pill" :disabled="!editor.isEditable" @mousedown.prevent @click="exitPostEditorContentBlock(editor)">{{ t('board.writePost.advanced.exitBlock') }}</button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" :aria-expanded="panel === 'outline'" @click="panel = panel === 'outline' ? null : 'outline'">{{ t('board.writePost.advanced.outline') }}</button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" :aria-expanded="panel === 'find'" @click="panel = panel === 'find' ? null : 'find'">{{ t('board.writePost.advanced.findReplace') }}</button>
      <button type="button" class="tiptap-btn tiptap-btn-pill" :aria-pressed="focused" @click="emit('update:focused', !focused)">{{ focused ? t('board.writePost.advanced.exitFocus') : t('board.writePost.advanced.focus') }}</button>
    </div>
    <div v-if="panel" class="post-editor-document-panel" @keydown.esc.stop="closePanel">
      <nav v-if="panel === 'outline'" :aria-label="t('board.writePost.advanced.outline')">
        <p v-if="!outline.length">{{ t('board.writePost.advanced.noHeadings') }}</p>
        <ol><li v-for="heading in outline" :key="heading.position" :style="{ paddingInlineStart: `${(heading.level - 1) * 0.75}rem` }">
          <button type="button" class="tiptap-btn tiptap-btn-pill" @click="jump(heading.position)">{{ heading.title || t('board.writePost.advanced.emptyHeading') }}</button>
        </li></ol>
      </nav>
      <div v-else class="post-editor-find-controls">
        <BaseInput v-model="query" :label="t('board.writePost.advanced.find')" @keydown.enter="findNext" />
        <BaseInput v-model="replacement" :label="t('board.writePost.advanced.replacement')" />
        <span aria-live="polite">{{ t('board.writePost.advanced.matchCount', { current: selectedMatch + 1, count: matches.length }) }}</span>
        <button type="button" class="tiptap-btn tiptap-btn-pill" :disabled="!matches.length" @click="navigate(-1)">{{ t('board.writePost.advanced.previous') }}</button>
        <button type="button" class="tiptap-btn tiptap-btn-pill" :disabled="!matches.length" @click="navigate(1)">{{ t('board.writePost.advanced.next') }}</button>
        <button type="button" class="tiptap-btn tiptap-btn-pill" :disabled="!editor.isEditable || selectedMatch < 0" @click="replacePostEditorText(editor, query, replacement, false)">{{ t('board.writePost.advanced.replace') }}</button>
        <button type="button" class="tiptap-btn tiptap-btn-pill" :disabled="!editor.isEditable || !matches.length" @click="replacePostEditorText(editor, query, replacement, true)">{{ t('board.writePost.advanced.replaceAll') }}</button>
      </div>
    </div>
  </div>
</template>
