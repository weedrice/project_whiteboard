<script setup lang="ts">
import { CheckCircle, Copy } from 'lucide-vue-next'
import DescriptionGrid from '@/components/admin/detail/DescriptionGrid.vue'
import DescriptionItem from '@/components/admin/detail/DescriptionItem.vue'
import DetailSection from '@/components/admin/detail/DetailSection.vue'
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
    body-class="space-y-5"
    footer-align="end"
    @close="emit('close')"
  >
    <div v-if="log">
      <DetailSection :title="$t('admin.errorLogs.detail.errorInfo')" compact>
        <DescriptionGrid gap-class="gap-2.5">
          <DescriptionItem :label="$t('admin.errorLogs.table.httpStatus')" label-class="detail-label" value-class="mt-1">
            <HttpStatusBadge :status="log.httpStatus" />
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.errorCode')" label-class="detail-label" value-class="detail-value font-mono">
            {{ log.errorCode || '-' }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.errorType')" label-class="detail-label" value-class="detail-value">
            {{ log.errorType }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.message')" label-class="detail-label" value-class="detail-value" full>
            {{ log.message }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <DetailSection :title="$t('admin.errorLogs.detail.requestInfo')" compact>
        <DescriptionGrid gap-class="gap-2.5">
          <DescriptionItem :label="$t('admin.errorLogs.table.requestMethod')" label-class="detail-label" value-class="detail-value font-mono">
            {{ log.requestMethod }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.requestUri')" label-class="detail-label" value-class="detail-value font-mono">
            {{ log.requestUri }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.userId')" label-class="detail-label" value-class="detail-value">
            {{ log.userId || '-' }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.ipAddress')" label-class="detail-label" value-class="detail-value font-mono">
            {{ log.ipAddress }}
          </DescriptionItem>
          <DescriptionItem label="User-Agent" label-class="detail-label" value-class="detail-value break-all text-xs" full>
            {{ log.userAgent || '-' }}
          </DescriptionItem>
          <DescriptionItem :label="$t('admin.errorLogs.table.createdAt')" label-class="detail-label" value-class="detail-value">
            {{ formatDateTimeOrDash(log.createdAt) }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>

      <DetailSection v-if="log.stackTrace" :title="$t('admin.errorLogs.detail.stackTrace')" compact>
        <template #actions>
          <BaseButton type="button" variant="secondary" size="sm" class="btn-copy-stack-trace" @click="emit('copyStackTrace')">
            <Copy class="h-3.5 w-3.5" />
            {{ $t('admin.errorLogs.actions.copy') }}
          </BaseButton>
        </template>
        <pre class="stack-trace-block">{{ log.stackTrace }}</pre>
      </DetailSection>

      <DetailSection v-if="log.isResolved === 'Y'" :title="$t('admin.errorLogs.detail.resolveInfo')" compact>
        <DescriptionGrid gap-class="gap-2.5">
          <DescriptionItem label="처리자 ID" label-class="detail-label" value-class="detail-value">
            {{ log.resolvedBy }}
          </DescriptionItem>
          <DescriptionItem label="처리 일시" label-class="detail-label" value-class="detail-value">
            {{ formatDateTimeOrDash(log.resolvedAt) }}
          </DescriptionItem>
          <DescriptionItem v-if="log.resolvedMemo" label="처리 메모" label-class="detail-label" value-class="detail-value" full>
            {{ log.resolvedMemo }}
          </DescriptionItem>
        </DescriptionGrid>
      </DetailSection>
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
