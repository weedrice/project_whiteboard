<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  label: string
  draftEnabled: boolean
  isSavingDraft: boolean
}>()

defineEmits<{
  saveDraft: []
}>()
</script>

<template>
  <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
    <div class="mb-3">
      <p class="nv-kicker">{{ $t('board.writePost.sections.draftState') }}</p>
    </div>
    <div class="flex flex-col gap-3">
      <p class="text-sm text-[var(--nv-ink-soft)]">{{ label }}</p>
      <BaseButton
        v-if="draftEnabled"
        type="button"
        variant="secondary"
        size="sm"
        full-width
        :disabled="isSavingDraft"
        @click="$emit('saveDraft')"
      >
        {{ isSavingDraft ? $t('board.writePost.draftStatus.saving') : $t('board.writePost.actions.saveDraft') }}
      </BaseButton>
    </div>
  </section>
</template>

<style scoped>
.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
