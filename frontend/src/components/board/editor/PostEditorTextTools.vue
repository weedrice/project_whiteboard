<script setup lang="ts">
import { isPostEditorFormattingDisabled } from '@/features/board/posts/editor/postEditorFormatting'
import { computed } from 'vue'
import type { Editor } from '@tiptap/core'
import { Undo2, Redo2, RemoveFormatting } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ editor: Editor; isRawHtmlBlockSelected: boolean }>()
const { t } = useI18n()
const headingLevels = [1, 2, 3, 4, 5, 6] as const
const blockStyle = computed(() => {
  for (const level of headingLevels) {
    if (props.editor.isActive('heading', { level })) return String(level)
  }
  return props.editor.isActive('paragraph') ? 'paragraph' : ''
})
const formattingDisabled = computed(() => isPostEditorFormattingDisabled(props.editor, props.isRawHtmlBlockSelected))

function canSetBlockStyle(value: string): boolean {
  if (formattingDisabled.value) return false
  if (value === 'paragraph') return props.editor.can().setParagraph()
  const level = headingLevels.find(level => String(level) === value)
  return level !== undefined && props.editor.can().setHeading({ level })
}
const blockStyleDisabled = computed(() => !canSetBlockStyle('paragraph')
  && !headingLevels.some(level => canSetBlockStyle(String(level))))

function setBlockStyle(value: string) {
  if (!canSetBlockStyle(value)) return
  if (value === 'paragraph') props.editor.chain().focus().setParagraph().run()
  else if (/^[1-6]$/.test(value)) {
    props.editor.chain().focus().setHeading({ level: Number(value) as 1 | 2 | 3 | 4 | 5 | 6 }).run()
  }
}

function clearFormatting() {
  if (formattingDisabled.value) return
  // Keep links, inline code, block structure and media intact.
  props.editor.chain().focus().unsetBold().unsetItalic().unsetUnderline().unsetStrike()
    .unsetMark('textStyle').unsetHighlight().unsetTextAlign().run()
}
</script>

<template>
  <button type="button" class="tiptap-btn" :title="t('board.writePost.editTools.undo')" :aria-label="t('board.writePost.editTools.undo')" :disabled="!editor.isEditable || !editor.can().undo()" @mousedown.prevent @click="editor.chain().focus().undo().run()">
    <Undo2 :size="16" aria-hidden="true" />
  </button>
  <button type="button" class="tiptap-btn" :title="t('board.writePost.editTools.redo')" :aria-label="t('board.writePost.editTools.redo')" :disabled="!editor.isEditable || !editor.can().redo()" @mousedown.prevent @click="editor.chain().focus().redo().run()">
    <Redo2 :size="16" aria-hidden="true" />
  </button>
  <button type="button" class="tiptap-btn" :title="t('board.writePost.editTools.clearFormatting')" :aria-label="t('board.writePost.editTools.clearFormatting')" :disabled="formattingDisabled" @mousedown.prevent @click="clearFormatting">
    <RemoveFormatting :size="16" aria-hidden="true" />
  </button>
  <select data-ui-native="specialized" class="tiptap-select text-xs" :value="blockStyle" :disabled="blockStyleDisabled" :aria-label="t('board.writePost.editTools.blockStyle')" @change="setBlockStyle(($event.target as HTMLSelectElement).value)">
    <option value="" disabled>{{ t('board.writePost.editTools.blockStyle') }}</option>
    <option value="paragraph" :disabled="!canSetBlockStyle('paragraph')">{{ t('board.writePost.editTools.paragraph') }}</option>
    <option v-for="level in headingLevels" :key="level" :value="String(level)" :disabled="!canSetBlockStyle(String(level))">{{ t('board.writePost.editTools.heading', { level }) }}</option>
  </select>
</template>
