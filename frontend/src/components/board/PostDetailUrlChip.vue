<script setup lang="ts">
import { Copy } from 'lucide-vue-next'

defineProps<{
  currentUrl: string
  compactUrl: string
  showCopyHint: boolean
}>()

const emit = defineEmits<{
  (e: 'copy-url'): void
}>()
</script>

<template>
  <div class="nv-post-content-top">
    <Transition name="fade">
      <span
        v-if="showCopyHint"
        class="nv-post-copy-hint"
      >
        {{ $t('common.messages.urlCopied') }}
      </span>
    </Transition>

    <button
      type="button"
      class="nv-post-url-chip"
      :title="currentUrl"
      :aria-label="$t('common.copy')"
      @click="emit('copy-url')"
    >
      <Copy class="h-3.5 w-3.5 flex-shrink-0" />
      <span>{{ compactUrl }}</span>
    </button>
  </div>
</template>

<style scoped>
.nv-post-copy-hint {
  background: var(--nv-success-bg);
  border: 1px solid var(--nv-success-border);
  border-radius: 9999px;
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-success-text);
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.35rem 0.65rem;
  position: absolute;
  right: 0;
  top: calc(100% + 0.5rem);
  white-space: nowrap;
  z-index: 20;
}

.nv-post-content-top {
  display: flex;
  justify-content: flex-end;
  position: relative;
}

.nv-post-url-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 68%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  cursor: pointer;
  color: var(--nv-muted);
  display: inline-flex;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  gap: 0.35rem;
  letter-spacing: -0.02em;
  max-width: min(16rem, 42vw);
  min-width: 0;
  padding: 0.55rem 0.7rem;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nv-post-url-chip:hover {
  background: var(--nv-surface-2);
  color: var(--nv-ink-soft);
}

.nv-post-url-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 639px) {
  .nv-post-url-chip {
    max-width: min(11rem, 48vw);
  }
}
</style>
