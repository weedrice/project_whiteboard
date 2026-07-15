<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PostVersion } from '@/api/post'
import { formatDate } from '@/utils/date'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'

defineProps<{
  isOpen: boolean
  versions?: PostVersion[]
  isLoading: boolean
  isError: boolean
}>()

const emit = defineEmits<{
  close: []
  retry: []
}>()

const { t } = useI18n()

function actionLabel(actionType: string) {
  const normalized = actionType.trim().toUpperCase()
  if (normalized === 'CREATE' || normalized === 'MODIFY' || normalized === 'DELETE') {
    return t(`board.postDetail.versionActions.${normalized}`)
  }
  return actionType
}
</script>

<template>
  <BaseModal
    :isOpen="isOpen"
    :title="t('board.postDetail.versionHistory')"
    size="lg"
    @close="emit('close')"
  >
    <div v-if="isLoading" class="py-8 text-center text-sm nv-text-subtle" role="status">
      {{ t('common.loading') }}
    </div>

    <div v-else-if="isError" class="py-8 text-center" role="alert">
      <p class="text-sm text-[var(--nv-danger-text)]">{{ t('board.postDetail.versionLoadFailed') }}</p>
      <BaseButton class="mt-4" variant="secondary" size="sm" @click="emit('retry')">
        {{ t('common.error.retry') }}
      </BaseButton>
    </div>

    <p v-else-if="!versions?.length" class="py-8 text-center text-sm nv-text-subtle">
      {{ t('board.postDetail.versionEmpty') }}
    </p>

    <ol v-else class="space-y-4" data-testid="post-version-timeline">
      <li
        v-for="version in versions"
        :key="version.versionId"
        class="relative border-l-2 border-[var(--nv-line)] py-1 pl-5"
      >
        <span class="absolute -left-[5px] top-2 h-2 w-2 rounded-full bg-[var(--nv-accent)]" aria-hidden="true" />
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-xs font-semibold text-[var(--nv-accent)]">{{ actionLabel(version.actionType) }}</span>
          <time class="text-xs nv-text-subtle" :datetime="version.createdAt">{{ formatDate(version.createdAt) }}</time>
        </div>
        <p class="mt-1 break-words text-sm nv-text">{{ version.title }}</p>
      </li>
    </ol>
  </BaseModal>
</template>
