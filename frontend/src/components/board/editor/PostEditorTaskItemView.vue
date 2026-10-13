<script setup lang="ts">
import { NodeViewWrapper, NodeViewContent, nodeViewProps } from '@tiptap/vue-3'
import { useI18n } from 'vue-i18n'
const props = defineProps(nodeViewProps)
const { t } = useI18n()
</script>
<template>
  <NodeViewWrapper as="li" data-type="taskItem" :data-checked="String(Boolean(props.node.attrs.checked))" class="post-editor-task-item">
    <button type="button" class="tiptap-btn post-editor-task-toggle" role="checkbox" :aria-checked="Boolean(props.node.attrs.checked)" :aria-label="t('board.writePost.advanced.toggleTask')" :disabled="!props.editor.isEditable" contenteditable="false" @mousedown.prevent @click="props.updateAttributes({ checked: !props.node.attrs.checked })">{{ props.node.attrs.checked ? '☑' : '☐' }}</button>
    <NodeViewContent class="post-editor-task-content" />
  </NodeViewWrapper>
</template>
