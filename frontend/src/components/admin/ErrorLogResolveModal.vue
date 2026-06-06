<script setup lang="ts">
import { CheckCircle } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
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
    close-aria-label="확인 처리 모달 닫기"
    close-button-class="btn-close"
    @close="emit('close')"
  >
    <div v-if="log" class="space-y-4">
      <div class="resolve-info">
        <p><strong>{{ $t('admin.errorLogs.table.errorType') }}:</strong> {{ log.errorType }}</p>
        <p><strong>{{ $t('admin.errorLogs.table.message') }}:</strong> {{ log.message }}</p>
      </div>
      <div>
        <label class="filter-label">{{ $t('admin.errorLogs.memoPlaceholder') }}</label>
        <textarea
          :value="memo"
          rows="3"
          class="filter-input w-full"
          :placeholder="$t('admin.errorLogs.memoPlaceholder')"
          @input="emit('update:memo', ($event.target as HTMLTextAreaElement).value)"
        />
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <BaseButton type="button" variant="primary" size="sm" class="btn-resolve" @click="emit('resolve')">
          <CheckCircle class="mr-1 h-4 w-4" />
          {{ $t('admin.errorLogs.actions.resolve') }}
        </BaseButton>
        <BaseButton type="button" variant="secondary" size="sm" class="btn-cancel" @click="emit('close')">
          {{ $t('admin.sanction.cancel') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.resolve-info p {
  margin: 4px 0;
  color: var(--nv-text-muted);
  font-size: 0.875rem;
}

.filter-label {
  color: var(--nv-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
}

.filter-input {
  padding: 6px 10px;
  border: 1px solid var(--nv-border);
  border-radius: 6px;
  background: var(--nv-surface);
  color: var(--nv-text);
  font-size: 0.8125rem;
}
</style>
