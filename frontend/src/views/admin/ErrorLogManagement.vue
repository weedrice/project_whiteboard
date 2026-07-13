<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle, Eye } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useErrorLogDetailModal } from '@/features/admin/error-logs/useErrorLogDetailModal'
import { useErrorLogListState } from '@/features/admin/error-logs/useErrorLogListState'
import AdminActionButton from '@/components/admin/AdminActionButton.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterActions from '@/components/admin/AdminFilterActions.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminMetricCard from '@/components/admin/AdminMetricCard.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminTableActions from '@/components/admin/AdminTableActions.vue'
import ErrorLogDetailModal from '@/components/admin/ErrorLogDetailModal.vue'
import ErrorLogResolveModal from '@/components/admin/ErrorLogResolveModal.vue'
import HttpStatusBadge from '@/components/admin/HttpStatusBadge.vue'
import ResolveStatusBadge from '@/components/admin/ResolveStatusBadge.vue'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { formatAdminPaginationSummary } from '@/utils/adminPaginationSummary'
import { formatDateTimeOrDash } from '@/utils/date'
import type { ErrorLogDetail, ErrorLogListItem } from '@/types'

const { t } = useI18n()

const {
  errorLogs,
  filterEndDate,
  filterErrorType,
  filterHttpStatus,
  filterIsResolved,
  filterStartDate,
  handleSearch,
  isLoading,
  page,
  resetFilters,
  statsData,
  totalElements,
  totalPages,
} = useErrorLogListState()

const {
  closeDetailModal,
  closeResolveModal,
  copyStackTrace,
  handleResolve,
  isDetailModalOpen,
  isResolveModalOpen,
  openDetailModal,
  openResolveModal,
  resolveMemo,
  resolveTargetLog,
  selectedLog,
} = useErrorLogDetailModal()

const columns = computed<TableColumn[]>(() => [
  { key: 'httpStatus', label: t('admin.errorLogs.table.httpStatus'), width: '8%' },
  { key: 'errorCode', label: t('admin.errorLogs.table.errorCode'), width: '13%', hideBelow: 'sm' },
  { key: 'errorType', label: t('admin.errorLogs.table.errorType'), width: '13%', hideBelow: 'lg' },
  { key: 'message', label: t('admin.errorLogs.table.message'), width: '20%' },
  { key: 'requestUri', label: t('admin.errorLogs.table.requestUri'), width: '16%', hideBelow: 'sm' },
  { key: 'ipAddress', label: t('admin.errorLogs.table.ipAddress'), width: '10%', hideBelow: 'lg' },
  { key: 'isResolved', label: t('admin.errorLogs.table.isResolved'), width: '8%' },
  { key: 'createdAt', label: t('admin.errorLogs.table.createdAt'), width: '10%', hideBelow: 'sm' },
  { key: 'actions', label: '', align: 'right', width: '8%' },
])
const filterChips = computed(() => [
  filterStartDate.value && { key: 'start', label: filterStartDate.value },
  filterEndDate.value && { key: 'end', label: filterEndDate.value },
  filterHttpStatus.value && { key: 'http', label: String(filterHttpStatus.value) },
  filterErrorType.value && { key: 'type', label: filterErrorType.value },
  filterIsResolved.value && { key: 'resolved', label: filterIsResolved.value },
].filter(Boolean) as Array<{ key: string; label: string }>)
const httpStatusModel = computed({
  get: () => filterHttpStatus.value ?? '',
  set: (value: string | number) => {
    filterHttpStatus.value = value === '' ? undefined : Number(value)
  },
})
const httpStatusOptions = computed(() => [
  { value: '', label: t('admin.errorLogs.filter.all') },
  ...[400, 401, 403, 404, 500].map((value) => ({ value, label: String(value) })),
])
const resolvedOptions = computed(() => [
  { value: '', label: t('admin.errorLogs.filter.all') },
  { value: 'N', label: t('admin.errorLogs.status.unresolved') },
  { value: 'Y', label: t('admin.errorLogs.status.resolved') },
])
function removeFilterChip(key: string) {
  if (key === 'start') filterStartDate.value = ''
  if (key === 'end') filterEndDate.value = ''
  if (key === 'http') filterHttpStatus.value = undefined
  if (key === 'type') filterErrorType.value = ''
  if (key === 'resolved') filterIsResolved.value = ''
}

function getRowClass(log: ErrorLogListItem): string {
  return log.isResolved === 'N' ? 'row-unresolved' : ''
}

function resolveFromDetail(log: ErrorLogDetail) {
  openResolveModal(log)
  closeDetailModal()
}
</script>

<template>
  <AdminDataPage
    :title="t('admin.errorLogs.title')"
    :description="t('admin.errorLogs.description')"
  >
    <template #filters>
      <div v-if="statsData" class="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <AdminMetricCard
          class="error-log-stat-card"
          :label="t('admin.errorLogs.stats.total')"
          :value="statsData.totalCount"
        />
        <AdminMetricCard
          class="error-log-stat-card error-log-stat-card--warning"
          :label="t('admin.errorLogs.stats.unresolved')"
          :value="statsData.unresolvedCount"
          tone="warning"
        />
        <AdminMetricCard
          class="error-log-stat-card error-log-stat-card--success"
          :label="t('admin.errorLogs.stats.resolved')"
          :value="statsData.resolvedCount"
          tone="success"
        />
      </div>

      <AdminFilterPanel collapsible :title="t('admin.errorLogs.title')" :chips="filterChips" @remove-chip="removeFilterChip">
        <div class="flex flex-wrap items-end gap-3">
          <AdminFilterField
            :label="t('admin.errorLogs.filter.startDate')"
            for-id="error-log-start-date"
            width="date"
          >
            <BaseInput id="error-log-start-date" v-model="filterStartDate" type="date" input-class="filter-input" />
          </AdminFilterField>
          <AdminFilterField
            :label="t('admin.errorLogs.filter.endDate')"
            for-id="error-log-end-date"
            width="date"
          >
            <BaseInput id="error-log-end-date" v-model="filterEndDate" type="date" input-class="filter-input" />
          </AdminFilterField>
          <AdminFilterField
            :label="t('admin.errorLogs.filter.httpStatus')"
            for-id="error-log-http-status"
            width="compact"
          >
            <BaseSelect id="error-log-http-status" v-model="httpStatusModel" :options="httpStatusOptions" input-class="filter-input" />
          </AdminFilterField>
          <AdminFilterField
            :label="t('admin.errorLogs.filter.isResolved')"
            for-id="error-log-is-resolved"
            width="compact"
          >
            <BaseSelect id="error-log-is-resolved" v-model="filterIsResolved" :options="resolvedOptions" input-class="filter-input" />
          </AdminFilterField>
          <AdminFilterField
            :label="t('admin.errorLogs.filter.errorType')"
            for-id="error-log-error-type"
            width="search"
          >
            <BaseInput
              id="error-log-error-type"
              v-model="filterErrorType"
              type="text"
              input-class="filter-input"
              placeholder="BusinessException..."
            />
          </AdminFilterField>
          <AdminFilterActions @search="handleSearch" @reset="resetFilters" />
        </div>
      </AdminFilterPanel>
    </template>

    <AdminPaginatedTable
      table-class="mt-4 error-log-table-wrapper"
      :columns="columns"
      :caption="t('admin.errorLogs.title')"
      :items="errorLogs"
      :loading="isLoading"
      :empty-text="t('admin.errorLogs.empty')"
      :row-class="getRowClass"
      row-key="errorLogId"
      :page="page"
      :total-pages="totalPages"
      :summary="formatAdminPaginationSummary(totalElements, { page, totalPages, t })"
      @page-change="page = $event"
    >
      <template #loading>
        <div class="loading-indicator">{{ t('common.loading') }}</div>
      </template>

      <template #cell-httpStatus="{ item: log }">
        <HttpStatusBadge :status="log.httpStatus" />
      </template>

      <template #cell-errorCode="{ item: log }">
        <span class="font-mono text-xs">{{ log.errorCode || '-' }}</span>
      </template>

      <template #cell-errorType="{ item: log }">
        <span class="text-xs">{{ log.errorType }}</span>
      </template>

      <template #cell-message="{ item: log }">
        <span class="message-cell" :title="log.message">{{ log.message }}</span>
      </template>

      <template #cell-requestUri="{ item: log }">
        <span class="uri-cell font-mono text-xs" :title="log.requestUri">{{ log.requestUri }}</span>
      </template>

      <template #cell-ipAddress="{ item: log }">
        <span class="font-mono text-xs">{{ log.ipAddress }}</span>
      </template>

      <template #cell-isResolved="{ item: log }">
        <ResolveStatusBadge
          :resolved="log.isResolved === 'Y'"
          :resolved-label="t('admin.errorLogs.status.resolved')"
          :unresolved-label="t('admin.errorLogs.status.unresolved')"
        />
      </template>

      <template #cell-createdAt="{ item: log }">
        <span class="text-xs">{{ formatDateTimeOrDash(log.createdAt) }}</span>
      </template>

      <template #cell-actions="{ item: log }">
        <AdminTableActions align-class="" gap-class="gap-1">
          <AdminActionButton
            type="button"
            icon-only
            :label="t('admin.errorLogs.actions.viewDetail')"
            @click="openDetailModal(log)"
          >
            <Eye class="h-4 w-4" aria-hidden="true" />
          </AdminActionButton>
          <AdminActionButton
            v-if="log.isResolved === 'N'"
            type="button"
            icon-only
            tone="success"
            class="btn-icon--resolve"
            :label="t('admin.errorLogs.actions.resolve')"
            @click="openResolveModal(log)"
          >
            <CheckCircle class="h-4 w-4" aria-hidden="true" />
          </AdminActionButton>
        </AdminTableActions>
      </template>
    </AdminPaginatedTable>

    <ErrorLogDetailModal
      :is-open="isDetailModalOpen"
      :log="selectedLog"
      @close="closeDetailModal"
      @copy-stack-trace="copyStackTrace"
      @resolve="resolveFromDetail"
    />
    <ErrorLogResolveModal
      v-model:memo="resolveMemo"
      :is-open="isResolveModalOpen"
      :log="resolveTargetLog"
      @close="closeResolveModal"
      @resolve="handleResolve"
    />
  </AdminDataPage>
</template>

<style scoped>
:deep(.filter-input) {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--nv-line);
  border-radius: 6px;
  background: var(--nv-surface);
  color: var(--nv-ink);
  font-size: 0.8125rem;
}

.error-log-table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--nv-line);
  border-radius: 8px;
  background: var(--nv-surface);
}

.loading-indicator {
  padding: 40px;
  color: var(--nv-muted);
  text-align: center;
}

.row-unresolved {
  background: var(--nv-warning-bg);
}

.message-cell,
.uri-cell {
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-cell {
  max-width: 200px;
}

.uri-cell {
  max-width: 180px;
}

</style>
