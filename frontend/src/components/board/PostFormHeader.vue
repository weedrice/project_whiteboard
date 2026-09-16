<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  pageTitle: string
  boardLabel: string
  hideBoardLabel?: boolean
  hidePreview?: boolean
  isSubmitting: boolean
  submitLabel: string
  submitDisabled?: boolean
}>()

defineEmits<{
  cancel: []
  preview: []
  submit: []
}>()
</script>

<template>
  <div class="nv-compose-header">
    <div class="min-w-0">
      <h1 class="truncate text-2xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)] sm:text-3xl">
        {{ pageTitle }}
      </h1>
      <p
        v-if="!hideBoardLabel"
        class="nv-compose-board-context mt-2 flex items-center gap-1 text-sm text-[var(--nv-ink-soft)]"
      >
        <span class="truncate">{{ boardLabel }}</span>
        <span class="shrink-0 text-[var(--nv-muted)]">
          {{ $t('common.board') }}
        </span>
      </p>
    </div>

    <div class="flex flex-wrap items-center justify-end gap-2">
      <BaseButton type="button" variant="secondary" size="sm" :disabled="isSubmitting" @click="$emit('cancel')">
        {{ $t('common.cancel') }}
      </BaseButton>
      <BaseButton v-if="!hidePreview" type="button" variant="secondary" size="sm" :disabled="isSubmitting" @click="$emit('preview')">
        {{ $t('board.writePost.actions.preview') }}
      </BaseButton>
      <BaseButton type="button" variant="primary" size="sm" :loading="isSubmitting" :disabled="isSubmitting || submitDisabled" @click="$emit('submit')">
        {{ submitLabel }}
      </BaseButton>
    </div>
  </div>
</template>

<style scoped>
.nv-compose-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

@media (max-width: 639px) {
  .nv-compose-header {
    position: sticky;
    top: 4rem;
    z-index: calc(var(--nv-z-sticky) - 1);
    padding: 0.75rem;
    border: 1px solid var(--nv-line);
    border-radius: 0.875rem;
    background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
    box-shadow: var(--nv-shadow-card);
    backdrop-filter: blur(12px);
  }
}
</style>
