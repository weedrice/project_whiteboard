<script setup lang="ts">
import BaseInput from '@/components/common/ui/BaseInput.vue'
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
  draftConflict: boolean
  scheduledAt: string
  showScheduler: boolean
}>()

defineEmits<{
  saveDraft: []
  reloadServerDraft: []
  keepLocalDraft: []
  'update:scheduledAt': [value: string]
}>()
</script>

<template>
  <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
    <section class="nv-compose-side-card nv-elevated-surface rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
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
      :draft-conflict="draftConflict"
      @save-draft="$emit('saveDraft')"
      @reload-server-draft="$emit('reloadServerDraft')"
      @keep-local-draft="$emit('keepLocalDraft')"
    />

    <section
      v-if="showScheduler"
      class="nv-compose-side-card nv-elevated-surface rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]"
    >
      <BaseInput
        id="scheduled-at"
        type="datetime-local"
        :model-value="scheduledAt"
        :label="$t('board.writePost.scheduleAt')"
        input-class="h-10"
        @update:model-value="$emit('update:scheduledAt', String($event))"
      />
      <p class="mt-2 text-xs nv-text-subtle">{{ $t('board.writePost.scheduleHelp') }}</p>
    </section>
  </aside>
</template>

<style scoped>
.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
