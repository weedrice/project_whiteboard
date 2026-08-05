<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'

defineProps<{
  label: string
  draftEnabled: boolean
  isSavingDraft: boolean
  isRestoringDraft: boolean
  draftConflict: boolean
  draftProtected: boolean
  protectedDraftForkAvailable: boolean
  draftDeleted: boolean
  restoreFailed: boolean
  multipleDraftsFound: boolean
  saveFailed: boolean
}>()

defineEmits<{
  saveDraft: []
  reloadServerDraft: []
  keepLocalDraft: []
  retryRestore: []
  saveDeletedAsNew: []
  discardDeleted: []
  saveProtectedAsNew: []
  discardProtected: []
}>()
</script>

<template>
  <section class="nv-compose-side-card nv-elevated-surface rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
    <div class="mb-3">
      <p class="nv-kicker">{{ $t('board.writePost.sections.draftState') }}</p>
    </div>
    <div class="flex flex-col gap-3">
      <p class="text-sm text-[var(--nv-ink-soft)]">{{ label }}</p>
      <template v-if="draftDeleted">
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          full-width
          :disabled="isSavingDraft || isRestoringDraft"
          @click="$emit('saveDeletedAsNew')"
        >
          {{ $t('board.writePost.draftStatus.saveAsNew') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          full-width
          :disabled="isSavingDraft || isRestoringDraft"
          @click="$emit('discardDeleted')"
        >
          {{ $t('board.writePost.draftStatus.discardLocal') }}
        </BaseButton>
      </template>
      <template v-else-if="draftConflict">
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          full-width
          :disabled="isSavingDraft || isRestoringDraft"
          @click="$emit('reloadServerDraft')"
        >
          {{ $t('board.writePost.draftStatus.reloadServer') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          full-width
          :disabled="isSavingDraft || isRestoringDraft"
          @click="$emit('keepLocalDraft')"
        >
          {{ $t('board.writePost.draftStatus.keepLocal') }}
        </BaseButton>
      </template>
      <template v-else-if="draftProtected">
        <template v-if="protectedDraftForkAvailable">
          <BaseButton
            type="button"
            variant="primary"
            size="sm"
            full-width
            :disabled="isSavingDraft || isRestoringDraft"
            @click="$emit('saveProtectedAsNew')"
          >
            {{ $t('board.writePost.draftStatus.saveAsNew') }}
          </BaseButton>
          <BaseButton
            type="button"
            variant="secondary"
            size="sm"
            full-width
            :disabled="isSavingDraft || isRestoringDraft"
            @click="$emit('discardProtected')"
          >
            {{ $t('board.writePost.draftStatus.discardLocal') }}
          </BaseButton>
        </template>
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          full-width
          to="/mypage/drafts"
        >
          {{ $t('board.writePost.draftStatus.openScheduledPosts') }}
        </BaseButton>
      </template>
      <BaseButton
        v-else-if="multipleDraftsFound"
        type="button"
        variant="secondary"
        size="sm"
        full-width
        to="/mypage/drafts"
      >
        {{ $t('board.writePost.draftStatus.openDrafts') }}
      </BaseButton>
      <BaseButton
        v-else-if="restoreFailed"
        type="button"
        variant="secondary"
        size="sm"
        full-width
        :disabled="isRestoringDraft"
        @click="$emit('retryRestore')"
      >
        {{ $t('board.writePost.draftStatus.retryRestore') }}
      </BaseButton>
      <BaseButton
        v-else-if="draftEnabled"
        type="button"
        variant="secondary"
        size="sm"
        full-width
        :disabled="isSavingDraft || isRestoringDraft"
        @click="$emit('saveDraft')"
      >
        {{ isSavingDraft
          ? $t('board.writePost.draftStatus.saving')
          : saveFailed
            ? $t('board.writePost.draftStatus.retryNow')
            : $t('board.writePost.actions.saveDraft') }}
      </BaseButton>
    </div>
  </section>
</template>

<style scoped>
.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
