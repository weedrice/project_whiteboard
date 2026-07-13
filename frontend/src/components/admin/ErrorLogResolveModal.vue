<script setup lang="ts">
import { CheckCircle } from 'lucide-vue-next'
import AdminModalActions from '@/components/admin/AdminModalActions.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import type { ErrorLogDetail, ErrorLogListItem } from '@/types'

defineProps<{
  isOpen: boolean
  log: ErrorLogListItem | ErrorLogDetail | null
  memo: string
}>()

const emit = defineEmits<{
  close: []
  resolve: []
  'update:memo': [value: string]
}>()
</script>

<template>
  <BaseModal
    :is-open="isOpen && !!log"
    :title="$t('admin.errorLogs.actions.resolve')"
    :close-aria-label="$t('admin.errorLogs.detail.resolveCloseAria')"
    close-button-class="btn-close"
    footer-align="end"
    @close="emit('close')"
  >
    <div v-if="log" class="space-y-4">
      <div class="resolve-info">
        <p><strong>{{ $t('admin.errorLogs.table.errorType') }}:</strong> {{ log.errorType }}</p>
        <p><strong>{{ $t('admin.errorLogs.table.message') }}:</strong> {{ log.message }}</p>
      </div>
      <div>
        <BaseTextarea
          id="error-log-resolve-memo"
          :model-value="memo"
          :label="$t('admin.errorLogs.memoPlaceholder')"
          rows="3"
          input-class="filter-input w-full"
          :placeholder="$t('admin.errorLogs.memoPlaceholder')"
          @update:model-value="emit('update:memo', $event)"
        />
      </div>
    </div>

    <template #footer>
      <AdminModalActions gap-class="gap-2">
        <BaseButton type="button" variant="primary" size="sm" class="btn-resolve" @click="emit('resolve')">
          <CheckCircle class="mr-1 h-4 w-4" />
          {{ $t('admin.errorLogs.actions.resolve') }}
        </BaseButton>
        <BaseButton type="button" variant="secondary" size="sm" class="btn-cancel" @click="emit('close')">
          {{ $t('admin.sanction.cancel') }}
        </BaseButton>
      </AdminModalActions>
    </template>
  </BaseModal>
</template>

<style scoped>
.resolve-info p {
  margin: 4px 0;
  color: var(--nv-ink-soft);
  font-size: 0.875rem;
}

:deep(.filter-input) {
  padding: 6px 10px;
  border: 1px solid var(--nv-line);
  border-radius: 6px;
  background: var(--nv-surface);
  color: var(--nv-ink);
  font-size: 0.8125rem;
}
</style>
