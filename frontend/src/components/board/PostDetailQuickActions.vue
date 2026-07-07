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
  <template v-if="visible">
    <div
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

    <div
      class="nv-post-mobile-actions xl:hidden"
      role="navigation"
      :aria-label="t('board.postDetail.quickActions')"
    >
      <button type="button" class="nv-post-mobile-action" @click="emit('comments')">
        <MessageSquare class="h-4 w-4" />
        <span>{{ t('board.postDetail.comments') }}</span>
      </button>
      <button type="button" class="nv-post-mobile-action" @click="emit('list')">
        <List class="h-4 w-4" />
        <span>{{ t('board.postDetail.toList') }}</span>
      </button>
      <button type="button" class="nv-post-mobile-action" @click="emit('top')">
        <ArrowLeft class="h-4 w-4 rotate-90" />
        <span>{{ t('board.postDetail.scrollTop') }}</span>
      </button>
    </div>
  </template>
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

.nv-post-mobile-actions {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 999px;
  bottom: calc(4.9rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-card);
  display: grid;
  gap: 0.25rem;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  left: 50%;
  max-width: min(26rem, calc(100vw - 1.5rem));
  padding: 0.25rem;
  position: fixed;
  transform: translateX(-50%);
  width: max-content;
  z-index: 35;
}

.nv-post-mobile-action {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 999px;
  color: var(--nv-ink-soft);
  cursor: pointer;
  display: inline-flex;
  font-size: 0.75rem;
  font-weight: 600;
  gap: 0.35rem;
  justify-content: center;
  min-height: 2.25rem;
  min-width: 4.25rem;
  padding: 0 0.65rem;
}

.nv-post-mobile-action:active,
.nv-post-mobile-action:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink);
}
</style>
