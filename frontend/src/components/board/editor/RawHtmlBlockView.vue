<script setup lang="ts">
import { computed, useId } from 'vue'
import { NodeViewWrapper, type NodeViewProps } from '@tiptap/vue-3'
import { Code2 } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import SandboxedHtmlFrame from '@/components/common/SandboxedHtmlFrame.vue'

const props = defineProps<NodeViewProps>()
const { t } = useI18n()
const html = computed(() => String(props.node.attrs.html ?? ''))
const descriptionId = `raw-html-block-description-${useId()}`
</script>

<template>
  <NodeViewWrapper
    class="raw-html-block"
    data-testid="raw-html-block"
    role="group"
    :aria-label="t('board.writePost.rawHtmlBlock.title')"
    :aria-describedby="descriptionId"
  >
    <div
      class="raw-html-block__header"
      contenteditable="false"
      data-drag-handle
    >
      <Code2 aria-hidden="true" :size="16" />
      <div>
        <strong>{{ t('board.writePost.rawHtmlBlock.title') }}</strong>
        <p :id="descriptionId">{{ t('board.writePost.rawHtmlBlock.description') }}</p>
      </div>
    </div>
    <div class="raw-html-block__preview" contenteditable="false">
      <SandboxedHtmlFrame
        :html="html"
        :title="t('board.writePost.rawHtmlBlock.previewTitle')"
        :min-height="320"
        loading="eager"
      />
    </div>
  </NodeViewWrapper>
</template>

<style scoped>
.raw-html-block {
  display: block;
  overflow: hidden;
  margin: 0.75rem 0;
  border: 1px solid var(--nv-line);
  border-radius: 0.75rem;
  background: var(--nv-surface);
}

.raw-html-block__header {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  border-bottom: 1px solid var(--nv-line);
  padding: 0.75rem 1rem;
  color: var(--nv-ink-soft);
  background: var(--nv-elevated);
  cursor: grab;
  user-select: none;
}

.raw-html-block__header:active {
  cursor: grabbing;
}

.raw-html-block__header strong {
  display: block;
  color: var(--nv-ink);
  font-size: 0.8125rem;
}

.raw-html-block__header p {
  margin: 0.125rem 0 0;
  font-size: 0.75rem;
  line-height: 1.4;
}

.raw-html-block__preview {
  max-height: min(52vh, 34rem);
  overflow: auto;
  padding: 0.75rem;
}

.raw-html-block.ProseMirror-selectednode {
  outline: 2px solid var(--nv-accent);
  outline-offset: 2px;
}
</style>
