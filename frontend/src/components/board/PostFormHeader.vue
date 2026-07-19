<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  pageTitle: string
  boardLabel: string
  hideBoardLabel?: boolean
  hidePreview?: boolean
  isSubmitting: boolean
  submitLabel: string
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
      <p v-if="!hideBoardLabel" class="mt-2 text-sm text-[var(--nv-ink-soft)]">
        {{ boardLabel }}
      </p>
    </div>

    <div class="flex flex-wrap items-center justify-end gap-2">
      <BaseButton type="button" variant="secondary" size="sm" :disabled="isSubmitting" @click="$emit('cancel')">
        {{ $t('common.cancel') }}
      </BaseButton>
      <BaseButton v-if="!hidePreview" type="button" variant="secondary" size="sm" :disabled="isSubmitting" @click="$emit('preview')">
        {{ $t('board.writePost.actions.preview') }}
      </BaseButton>
      <BaseButton type="button" variant="primary" size="sm" :loading="isSubmitting" :disabled="isSubmitting" @click="$emit('submit')">
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
</style>
