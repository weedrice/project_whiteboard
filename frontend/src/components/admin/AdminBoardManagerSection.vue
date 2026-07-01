<template>
  <div class="rounded-lg border nv-border p-4">
    <h3 class="text-sm font-semibold nv-title">{{ t('admin.boards.managerTitle') }}</h3>
    <div v-if="loading" class="mt-3 flex justify-center">
      <BaseSpinner />
    </div>
    <div v-else class="mt-3 space-y-3">
      <p class="text-sm nv-text-muted">
        {{ currentManagerLabel }}
      </p>
      <BaseButton :disabled="isAssigningManager" @click="emit('open')">
        {{ isAssigningManager ? t('common.messages.saving') : t('admin.boards.chooseManager') }}
      </BaseButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

defineProps<{
  loading: boolean
  currentManagerLabel: string
  isAssigningManager: boolean
}>()

const emit = defineEmits<{
  (event: 'open'): void
}>()

const { t } = useI18n()
</script>
