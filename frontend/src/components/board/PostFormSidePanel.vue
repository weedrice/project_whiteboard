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
  draftEnabled: boolean
  isSavingDraft: boolean
}>()

defineEmits<{
  saveDraft: []
}>()
</script>

<template>
  <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
    <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
      <PostFormMetadataPanel
        layout="desktop"
        v-bind="metadataPanelProps"
        v-on="metadataPanelHandlers"
      />
    </section>

    <PostDraftStatusPanel
      :label="draftStatusLabel"
      :draft-enabled="draftEnabled"
      :is-saving-draft="isSavingDraft"
      @save-draft="$emit('saveDraft')"
    />
  </aside>
</template>

<style scoped>
.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
