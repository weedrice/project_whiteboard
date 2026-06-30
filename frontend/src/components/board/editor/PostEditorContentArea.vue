<script setup lang="ts">
import { EditorContent, type Editor } from '@tiptap/vue-3'

defineProps<{
  editor: Editor | undefined
  isDraggingImage: boolean
  dropImageHint: string
}>()

const emit = defineEmits<{
  (event: 'content-mousedown', value: MouseEvent): void
  (event: 'content-paste', value: ClipboardEvent): void
  (event: 'content-drop', value: DragEvent): void
  (event: 'content-dragenter', value: DragEvent): void
  (event: 'content-dragleave', value: DragEvent): void
}>()
</script>

<template>
  <div
    class="tiptap-content flex-1 min-h-0 overflow-auto cursor-text"
    :class="{ 'tiptap-content--dragging-image': isDraggingImage }"
    @mousedown="emit('content-mousedown', $event)"
    @paste="emit('content-paste', $event)"
    @drop="emit('content-drop', $event)"
    @dragenter.prevent="emit('content-dragenter', $event)"
    @dragleave="emit('content-dragleave', $event)"
    @dragover.prevent
  >
    <EditorContent :editor="editor" />
    <div v-if="isDraggingImage" class="image-drop-overlay" aria-live="polite">
      {{ dropImageHint }}
    </div>
  </div>
</template>
