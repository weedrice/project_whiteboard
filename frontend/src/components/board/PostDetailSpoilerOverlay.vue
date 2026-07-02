<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  timeLeft: number
}>()

const emit = defineEmits<{
  (e: 'reveal-spoiler'): void
}>()
</script>

<template>
  <div class="nv-post-spoiler">
    <div class="nv-post-spoiler-card">
      <h3 class="text-lg font-semibold text-[var(--nv-ink)]">
        {{ $t('board.postDetail.spoilerWarning') }}
      </h3>
      <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
        {{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}
      </p>
      <div class="nv-post-spoiler-actions">
        <BaseButton size="sm" @click="emit('reveal-spoiler')">
          {{ $t('board.postDetail.revealSpoiler') }}
        </BaseButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nv-post-spoiler {
  align-items: flex-start;
  background: color-mix(in srgb, var(--nv-surface) 45%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  overflow-y: auto;
  padding: clamp(0.75rem, 4vh, 1.5rem);
  position: absolute;
}

.nv-post-spoiler-card {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.5rem;
  box-shadow: var(--nv-shadow-card);
  margin-block: auto;
  max-height: 100%;
  max-width: 22rem;
  overflow-y: auto;
  padding: clamp(1rem, 3vw, 1.5rem);
  text-align: center;
  width: 100%;
}

.nv-post-spoiler-actions {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}
</style>
