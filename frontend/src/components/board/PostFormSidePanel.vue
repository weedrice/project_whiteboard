<script setup lang="ts">
import PostDraftStatusPanel from '@/components/board/PostDraftStatusPanel.vue'
import PostFormMetadataPanel from '@/components/board/PostFormMetadataPanel.vue'
import type {
  PostFormMetadataPanelHandlers,
  PostFormMetadataPanelProps,
} from '@/features/board/posts/form/usePostFormMetadataBindings'

defineProps<{
  metadataPanelProps: PostFormMetadataPanelProps
  metadataPanelHandlers: PostFormMetadataPanelHandlers
  draftStatusLabel: string
}>()
</script>

<template>
  <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
    <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
      <div class="mb-4">
        <p class="nv-compose-kicker">{{ $t('board.writePost.sections.metadata') }}</p>
        <h3 class="text-lg font-semibold text-[var(--nv-ink)]">{{ $t('board.writePost.sections.postSettings') }}</h3>
      </div>

      <PostFormMetadataPanel
        layout="desktop"
        v-bind="metadataPanelProps"
        v-on="metadataPanelHandlers"
      />
    </section>

    <PostDraftStatusPanel :label="draftStatusLabel" />
  </aside>
</template>

<style scoped>
.nv-compose-kicker {
  color: var(--nv-muted);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
