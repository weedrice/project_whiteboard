<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import type { SanitizedHtml } from '@/utils/sanitize'

defineProps<{
  isOpen: boolean
  boardLabel?: string
  postTitle: string
  tags: string[]
  html: SanitizedHtml
  hideBoardLabel?: boolean
  hideTags?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()
</script>

<template>
  <BaseModal
    :is-open="isOpen"
    :title="t('board.writePost.preview.title')"
    size="2xl"
    mobile-fit-content
    @close="emit('close')"
  >
    <div class="space-y-4">
      <div>
        <p
          v-if="!hideBoardLabel"
          class="text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]"
        >
          {{ boardLabel }}
        </p>
        <h3 class="mt-2 text-2xl font-semibold text-[var(--nv-ink)]">
          {{ postTitle || t('board.writePost.preview.untitledPost') }}
        </h3>
      </div>
      <div v-if="!hideTags && tags.length" class="flex flex-wrap gap-2">
        <span
          v-for="tag in tags"
          :key="tag"
          class="rounded-full border border-[var(--nv-line)] px-3 py-1 text-xs text-[var(--nv-ink-soft)]"
        >
          #{{ tag }}
        </span>
      </div>
      <article class="nv-rich-content prose max-w-none dark:prose-invert" v-html="html" />
    </div>
    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton type="button" variant="secondary" size="sm" @click="emit('close')">
          {{ t('common.close') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>
