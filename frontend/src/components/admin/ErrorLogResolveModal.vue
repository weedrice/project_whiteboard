<script setup lang="ts">
import { CheckCircle, X } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
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
  <Teleport to="body">
    <div v-if="isOpen && log" class="modal-overlay" @click.self="emit('close')">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ $t('admin.errorLogs.actions.resolve') }}</h3>
          <button type="button" class="btn-close" aria-label="확인 처리 모달 닫기" @click="emit('close')">
            <X class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div class="modal-body">
          <div class="resolve-info">
            <p><strong>{{ $t('admin.errorLogs.table.errorType') }}:</strong> {{ log.errorType }}</p>
            <p><strong>{{ $t('admin.errorLogs.table.message') }}:</strong> {{ log.message }}</p>
          </div>
          <div class="mt-4">
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

        <div class="modal-footer">
          <BaseButton type="button" variant="primary" size="sm" class="btn-resolve" @click="emit('resolve')">
            <CheckCircle class="mr-1 h-4 w-4" />
            {{ $t('admin.errorLogs.actions.resolve') }}
          </BaseButton>
          <BaseButton type="button" variant="secondary" size="sm" class="btn-cancel" @click="emit('close')">
            {{ $t('admin.sanction.cancel') }}
          </BaseButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.5);
}

.modal-content {
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  border-radius: 12px;
  background: var(--nv-surface);
  color: var(--nv-text);
  box-shadow: var(--nv-shadow-popup);
}

.modal-header,
.modal-footer {
  display: flex;
  align-items: center;
  padding: 16px 20px;
}

.modal-header {
  justify-content: space-between;
  border-bottom: 1px solid var(--nv-border);
}

.modal-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: var(--nv-text);
}

.modal-body {
  padding: 20px;
}

.modal-footer {
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--nv-border);
}

.btn-close {
  padding: 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--nv-text-muted);
  cursor: pointer;
}

.btn-close:hover {
  background: var(--nv-surface-hover);
}

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
