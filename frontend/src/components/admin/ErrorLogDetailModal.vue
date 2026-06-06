<script setup lang="ts">
import { CheckCircle, Copy } from 'lucide-vue-next'
import HttpStatusBadge from '@/components/admin/HttpStatusBadge.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { formatDateTimeOrDash } from '@/utils/date'
import type { ErrorLogDetail } from '@/types'

defineProps<{
  isOpen: boolean
  log: ErrorLogDetail | null
}>()

const emit = defineEmits<{
  close: []
  copyStackTrace: []
  resolve: [log: ErrorLogDetail]
}>()
</script>

<template>
  <BaseModal
    :is-open="isOpen && !!log"
    :title="$t('admin.errorLogs.detail.title')"
    size="2xl"
    close-aria-label="상세 모달 닫기"
    close-button-class="btn-close"
    @close="emit('close')"
  >
    <div v-if="log" class="space-y-5">
      <section class="detail-section">
        <h4 class="detail-section-title">{{ $t('admin.errorLogs.detail.errorInfo') }}</h4>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.httpStatus') }}</span>
            <HttpStatusBadge :status="log.httpStatus" />
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.errorCode') }}</span>
            <span class="detail-value font-mono">{{ log.errorCode || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.errorType') }}</span>
            <span class="detail-value">{{ log.errorType }}</span>
          </div>
          <div class="detail-item detail-item--full">
            <span class="detail-label">{{ $t('admin.errorLogs.table.message') }}</span>
            <span class="detail-value">{{ log.message }}</span>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <h4 class="detail-section-title">{{ $t('admin.errorLogs.detail.requestInfo') }}</h4>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.requestMethod') }}</span>
            <span class="detail-value font-mono">{{ log.requestMethod }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.requestUri') }}</span>
            <span class="detail-value font-mono">{{ log.requestUri }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.userId') }}</span>
            <span class="detail-value">{{ log.userId || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.ipAddress') }}</span>
            <span class="detail-value font-mono">{{ log.ipAddress }}</span>
          </div>
          <div class="detail-item detail-item--full">
            <span class="detail-label">User-Agent</span>
            <span class="detail-value break-all text-xs">{{ log.userAgent || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">{{ $t('admin.errorLogs.table.createdAt') }}</span>
            <span class="detail-value">{{ formatDateTimeOrDash(log.createdAt) }}</span>
          </div>
        </div>
      </section>

      <section v-if="log.stackTrace" class="detail-section">
        <div class="stack-trace-header">
          <h4 class="detail-section-title stack-trace-title">{{ $t('admin.errorLogs.detail.stackTrace') }}</h4>
          <button type="button" class="btn-copy-stack-trace" @click="emit('copyStackTrace')">
            <Copy class="h-3.5 w-3.5" />
            {{ $t('admin.errorLogs.actions.copy') }}
          </button>
        </div>
        <pre class="stack-trace-block">{{ log.stackTrace }}</pre>
      </section>

      <section v-if="log.isResolved === 'Y'" class="detail-section">
        <h4 class="detail-section-title">{{ $t('admin.errorLogs.detail.resolveInfo') }}</h4>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">처리자 ID</span>
            <span class="detail-value">{{ log.resolvedBy }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">처리 일시</span>
            <span class="detail-value">{{ formatDateTimeOrDash(log.resolvedAt) }}</span>
          </div>
          <div v-if="log.resolvedMemo" class="detail-item detail-item--full">
            <span class="detail-label">처리 메모</span>
            <span class="detail-value">{{ log.resolvedMemo }}</span>
          </div>
        </div>
      </section>
    </div>

    <template v-if="log" #footer>
      <div class="flex justify-end gap-2">
        <BaseButton
          v-if="log.isResolved === 'N'"
          type="button"
          variant="primary"
          size="sm"
          class="btn-resolve"
          @click="emit('resolve', log)"
        >
          <CheckCircle class="mr-1 h-4 w-4" />
          {{ $t('admin.errorLogs.actions.resolve') }}
        </BaseButton>
        <BaseButton type="button" variant="secondary" size="sm" class="btn-cancel" @click="emit('close')">
          {{ $t('common.close') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.detail-section-title {
  margin-bottom: 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--nv-border);
  color: var(--nv-text);
  font-size: 0.875rem;
  font-weight: 600;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-item--full {
  grid-column: 1 / -1;
}

.detail-label {
  color: var(--nv-text-subtle);
  font-size: 0.6875rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.detail-value {
  color: var(--nv-text);
  font-size: 0.8125rem;
  word-break: break-all;
}

.stack-trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stack-trace-title {
  flex: 1;
  margin-bottom: 0;
}

.btn-copy-stack-trace {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--nv-border);
  border-radius: 6px;
  background: var(--nv-surface);
  color: var(--nv-text-muted);
  font-size: 0.75rem;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}

.btn-copy-stack-trace:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}

.stack-trace-block {
  max-height: 300px;
  overflow-x: auto;
  padding: 12px 16px;
  border-radius: 6px;
  background: #111827;
  color: #e5e7eb;
  font-family: 'Fira Code', 'JetBrains Mono', monospace;
  font-size: 0.6875rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.dark .stack-trace-block {
  background: #030712;
  color: #d1d5db;
}
</style>
