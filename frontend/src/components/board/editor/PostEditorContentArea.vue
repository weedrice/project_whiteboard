<script setup lang="ts">
import { EditorContent, type Editor } from '@tiptap/vue-3'

const props = withDefaults(defineProps<{
  editor: Editor | undefined
  isDraggingImage: boolean
  dropImageHint: string
  readOnly?: boolean
}>(), {
  readOnly: false,
})

const emit = defineEmits<{
  (event: 'content-mousedown', value: MouseEvent): void
  (event: 'content-paste', value: ClipboardEvent): void
  (event: 'content-drop', value: DragEvent): void
  (event: 'content-dragenter', value: DragEvent): void
  (event: 'content-dragleave', value: DragEvent): void
}>()

function handlePaste(event: ClipboardEvent) {
  if (props.readOnly) {
    event.preventDefault()
    return
  }
  emit('content-paste', event)
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  if (!props.readOnly) emit('content-drop', event)
}

function handleDragEnter(event: DragEvent) {
  event.preventDefault()
  if (!props.readOnly) emit('content-dragenter', event)
}
</script>

<template>
  <div
    class="tiptap-content flex-1 min-h-0 overflow-auto"
    :class="{
      'cursor-text': !readOnly,
      'tiptap-content--dragging-image': isDraggingImage && !readOnly,
    }"
    @mousedown="!readOnly && emit('content-mousedown', $event)"
    @paste="handlePaste"
    @drop="handleDrop"
    @dragenter="handleDragEnter"
    @dragleave="!readOnly && emit('content-dragleave', $event)"
    @dragover.prevent
  >
    <EditorContent :editor="editor" />
    <div v-if="isDraggingImage" class="image-drop-overlay" aria-live="polite">
      {{ dropImageHint }}
    </div>
  </div>
</template>
