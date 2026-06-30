<script setup lang="ts">
import { ArrowLeft, List, MessageSquare } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'comments'): void
  (e: 'list'): void
  (e: 'top'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div
    v-if="visible"
    class="nv-post-board-actions hidden xl:flex"
    role="navigation"
    :aria-label="t('board.postDetail.quickActions')"
  >
    <button
      type="button"
      class="nv-post-board-action"
      :aria-label="t('board.postDetail.comments')"
      :title="t('board.postDetail.comments')"
      @click="emit('comments')"
    >
      <MessageSquare class="h-4 w-4" />
    </button>
    <button
      type="button"
      class="nv-post-board-action"
      :aria-label="t('board.postDetail.toList')"
      :title="t('board.postDetail.toList')"
      @click="emit('list')"
    >
      <List class="h-4 w-4" />
    </button>
    <button
      type="button"
      class="nv-post-board-action"
      :aria-label="t('board.postDetail.scrollTop')"
      :title="t('board.postDetail.scrollTop')"
      @click="emit('top')"
    >
      <ArrowLeft class="h-4 w-4 rotate-90" />
    </button>
  </div>
</template>

<style scoped>
.nv-post-board-actions {
  flex-direction: column;
  gap: 0.75rem;
  position: fixed;
  right: clamp(0.5rem, calc((100vw - 1120px) / 2 - 4.5rem), 3.5rem);
  top: 10rem;
  z-index: 25;
}

.nv-post-board-action {
  align-items: center;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 1.1rem;
  box-shadow: var(--nv-shadow-card);
  color: var(--nv-ink-soft);
  display: inline-flex;
  gap: 0.55rem;
  height: 2.9rem;
  justify-content: center;
  min-height: 3rem;
  min-width: 0;
  padding: 0;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
  width: 2.9rem;
}

.nv-post-board-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}
</style>
