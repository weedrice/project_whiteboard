<template>
  <div class="rounded-lg border nv-border p-4">
    <h3 class="text-sm font-semibold nv-title">{{ t('admin.boards.managerTitle') }}</h3>
    <AdminContentState :loading="loading" :error="error" padding-class="mt-3" @retry="emit('retry')">
      <div class="mt-3 space-y-3">
      <p class="text-sm nv-text-muted">
        {{ currentManagerLabel }}
      </p>
      <BaseButton :disabled="isAssigningManager" @click="emit('open')">
        {{ isAssigningManager ? t('common.messages.saving') : t('admin.boards.chooseManager') }}
      </BaseButton>
      </div>
    </AdminContentState>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminContentState from '@/components/admin/AdminContentState.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

withDefaults(defineProps<{
  loading: boolean
  error?: boolean
  currentManagerLabel: string
  isAssigningManager: boolean
}>(), { error: false })

const emit = defineEmits<{
  (event: 'open'): void
  (event: 'retry'): void
}>()

const { t } = useI18n()
</script>
