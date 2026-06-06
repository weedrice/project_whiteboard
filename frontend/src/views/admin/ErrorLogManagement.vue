<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle, Eye, Search } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useErrorLogDetailModal } from '@/composables/useErrorLogDetailModal'
import { useErrorLogListState } from '@/composables/useErrorLogListState'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import ErrorLogDetailModal from '@/components/admin/ErrorLogDetailModal.vue'
import ErrorLogResolveModal from '@/components/admin/ErrorLogResolveModal.vue'
import HttpStatusBadge from '@/components/admin/HttpStatusBadge.vue'
import ResolveStatusBadge from '@/components/admin/ResolveStatusBadge.vue'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
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
  { key: 'errorCode', label: t('admin.errorLogs.table.errorCode'), width: '13%' },
  { key: 'errorType', label: t('admin.errorLogs.table.errorType'), width: '13%' },
  { key: 'message', label: t('admin.errorLogs.table.message'), width: '20%' },
  { key: 'requestUri', label: t('admin.errorLogs.table.requestUri'), width: '16%' },
  { key: 'ipAddress', label: t('admin.errorLogs.table.ipAddress'), width: '10%' },
  { key: 'isResolved', label: t('admin.errorLogs.table.isResolved'), width: '8%' },
  { key: 'createdAt', label: t('admin.errorLogs.table.createdAt'), width: '10%' },
  { key: 'actions', label: '', align: 'right', width: '8%' },
])

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
        <div class="error-log-stat-card">
          <div class="stat-label">{{ t('admin.errorLogs.stats.total') }}</div>
          <div class="stat-value">{{ statsData.totalCount }}</div>
        </div>
        <div class="error-log-stat-card error-log-stat-card--warning">
          <div class="stat-label">{{ t('admin.errorLogs.stats.unresolved') }}</div>
          <div class="stat-value">{{ statsData.unresolvedCount }}</div>
        </div>
        <div class="error-log-stat-card error-log-stat-card--success">
          <div class="stat-label">{{ t('admin.errorLogs.stats.resolved') }}</div>
          <div class="stat-value">{{ statsData.resolvedCount }}</div>
        </div>
      </div>

      <AdminFilterPanel>
        <div class="flex flex-wrap items-end gap-3">
          <AdminFilterField :label="t('admin.errorLogs.filter.startDate')" width-class="w-40">
            <input v-model="filterStartDate" type="date" class="filter-input" />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.errorLogs.filter.endDate')" width-class="w-40">
            <input v-model="filterEndDate" type="date" class="filter-input" />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.errorLogs.filter.httpStatus')" width-class="w-36">
            <select v-model="filterHttpStatus" class="filter-input">
              <option :value="undefined">{{ t('admin.errorLogs.filter.all') }}</option>
              <option :value="400">400</option>
              <option :value="401">401</option>
              <option :value="403">403</option>
              <option :value="404">404</option>
              <option :value="500">500</option>
            </select>
          </AdminFilterField>
          <AdminFilterField :label="t('admin.errorLogs.filter.isResolved')" width-class="w-36">
            <select v-model="filterIsResolved" class="filter-input">
              <option value="">{{ t('admin.errorLogs.filter.all') }}</option>
              <option value="N">{{ t('admin.errorLogs.status.unresolved') }}</option>
              <option value="Y">{{ t('admin.errorLogs.status.resolved') }}</option>
            </select>
          </AdminFilterField>
          <AdminFilterField :label="t('admin.errorLogs.filter.errorType')" width-class="w-52">
            <input
              v-model="filterErrorType"
              type="text"
              class="filter-input"
              placeholder="BusinessException..."
            />
          </AdminFilterField>
          <div class="filter-item filter-item--actions flex gap-2">
            <button type="button" class="btn-search" @click="handleSearch">
              <Search class="mr-1 h-4 w-4" />
              검색
            </button>
            <button type="button" class="btn-reset" @click="resetFilters">
              초기화
            </button>
          </div>
        </div>
      </AdminFilterPanel>
    </template>

    <BaseTable
      class="mt-4 error-log-table-wrapper"
      :columns="columns"
      :items="errorLogs"
      :loading="isLoading"
      empty-text="에러 로그가 없습니다."
      :row-class="getRowClass"
      row-key="errorLogId"
    >
      <template #loading>
        <div class="loading-indicator">로딩 중...</div>
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
        <div class="action-buttons">
          <button
            type="button"
            class="btn-icon"
            :title="t('admin.errorLogs.actions.viewDetail')"
            :aria-label="t('admin.errorLogs.actions.viewDetail')"
            @click="openDetailModal(log)"
          >
            <Eye class="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            v-if="log.isResolved === 'N'"
            type="button"
            class="btn-icon btn-icon--resolve"
            :title="t('admin.errorLogs.actions.resolve')"
            :aria-label="t('admin.errorLogs.actions.resolve')"
            @click="openResolveModal(log)"
          >
            <CheckCircle class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </template>
    </BaseTable>

    <template #footer>
      <AdminPaginationFooter
        :page="page"
        :total-pages="totalPages"
        :summary="`총 ${totalElements.toLocaleString()}건 (${page + 1} / ${totalPages} 페이지)`"
        @page-change="page = $event"
      />
    </template>

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
.error-log-stat-card {
  padding: 16px 20px;
  border: 1px solid var(--nv-border);
  border-radius: 8px;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-card);
}

.error-log-stat-card--warning {
  border-left: 4px solid var(--nv-warning);
}

.error-log-stat-card--success {
  border-left: 4px solid var(--nv-success);
}

.stat-label {
  color: var(--nv-text-subtle);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.stat-value {
  margin-top: 4px;
  color: var(--nv-text);
  font-size: 1.5rem;
  font-weight: 700;
}

.filter-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--nv-border);
  border-radius: 6px;
  background: var(--nv-surface);
  color: var(--nv-text);
  font-size: 0.8125rem;
}

.btn-search,
.btn-reset {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-search {
  border: 0;
  background: var(--nv-accent);
  color: white;
}

.btn-search:hover {
  background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.btn-reset {
  border: 1px solid var(--nv-border);
  background: var(--nv-surface);
  color: var(--nv-text-muted);
}

.btn-reset:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}

.error-log-table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--nv-border);
  border-radius: 8px;
  background: var(--nv-surface);
}

.loading-indicator {
  padding: 40px;
  color: var(--nv-text-subtle);
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

.action-buttons {
  display: flex;
  gap: 4px;
}

.btn-icon {
  padding: 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--nv-text-subtle);
  cursor: pointer;
  transition: all 0.15s;
}

.btn-icon:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}

.btn-icon--resolve {
  color: var(--nv-success-text);
}

.btn-icon--resolve:hover {
  background: var(--nv-success-bg);
  color: var(--nv-success-text);
}
</style>
