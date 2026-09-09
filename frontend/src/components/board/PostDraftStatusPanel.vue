<script setup lang="ts">
import PostDraftActions from '@/components/board/PostDraftActions.vue'
import type { DraftActionId, DraftPresentation } from '@/features/board/posts/draft/postDraftContracts'

defineProps<{
  presentation: DraftPresentation
}>()

defineEmits<{
  action: [action: DraftActionId]
}>()
</script>

<template>
  <section class="nv-compose-side-card nv-elevated-surface rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
    <div class="mb-3">
      <p class="nv-kicker">{{ $t('board.writePost.sections.draftState') }}</p>
    </div>
    <div class="flex flex-col gap-3">
      <p class="text-sm text-[var(--nv-ink-soft)]">{{ presentation.label }}</p>
      <PostDraftActions :presentation="presentation" @action="$emit('action', $event)" />
    </div>
  </section>
</template>

<style scoped>
.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
