<script setup lang="ts">
import { computed } from 'vue'
import { useErrorLogDetailModal } from '@/composables/useErrorLogDetailModal'
import { useErrorLogListState } from '@/composables/useErrorLogListState'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
import { useI18n } from 'vue-i18n'
import { Eye, CheckCircle, X, Search, Copy } from 'lucide-vue-next'
import { formatDateTimeOrDash } from '@/utils/date'
import type { ErrorLogListItem } from '@/types'

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
    totalPages
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
    selectedLog
} = useErrorLogDetailModal()

function getHttpStatusClass(status: number): string {
    if (status >= 500) return 'status-500'
    if (status >= 400) return 'status-400'
    return 'status-other'
}

const columns = computed<TableColumn[]>(() => [
    { key: 'httpStatus', label: t('admin.errorLogs.table.httpStatus'), width: '8%' },
    { key: 'errorCode', label: t('admin.errorLogs.table.errorCode'), width: '13%' },
    { key: 'errorType', label: t('admin.errorLogs.table.errorType'), width: '13%' },
    { key: 'message', label: t('admin.errorLogs.table.message'), width: '20%' },
    { key: 'requestUri', label: t('admin.errorLogs.table.requestUri'), width: '16%' },
    { key: 'ipAddress', label: t('admin.errorLogs.table.ipAddress'), width: '10%' },
    { key: 'isResolved', label: t('admin.errorLogs.table.isResolved'), width: '8%' },
    { key: 'createdAt', label: t('admin.errorLogs.table.createdAt'), width: '10%' },
    { key: 'actions', label: '', align: 'right', width: '8%' }
])

function getRowClass(log: ErrorLogListItem): string {
    return log.isResolved === 'N' ? 'row-unresolved' : ''
}

</script>

<template>
    <div>
        <!-- 헤더 -->
        <AdminPageHeader :title="t('admin.errorLogs.title')" :description="t('admin.errorLogs.description')" />

        <!-- 통계 카드 -->
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

        <!-- 필터 -->
        <div class="mt-6 error-log-filter-bar">
            <div class="filter-grid">
                <div class="filter-item">
                    <label class="filter-label">{{ t('admin.errorLogs.filter.startDate') }}</label>
                    <input v-model="filterStartDate" type="date" class="filter-input" />
                </div>
                <div class="filter-item">
                    <label class="filter-label">{{ t('admin.errorLogs.filter.endDate') }}</label>
                    <input v-model="filterEndDate" type="date" class="filter-input" />
                </div>
                <div class="filter-item">
                    <label class="filter-label">{{ t('admin.errorLogs.filter.httpStatus') }}</label>
                    <select v-model="filterHttpStatus" class="filter-input">
                        <option :value="undefined">{{ t('admin.errorLogs.filter.all') }}</option>
                        <option :value="400">400</option>
                        <option :value="401">401</option>
                        <option :value="403">403</option>
                        <option :value="404">404</option>
                        <option :value="500">500</option>
                    </select>
                </div>
                <div class="filter-item">
                    <label class="filter-label">{{ t('admin.errorLogs.filter.isResolved') }}</label>
                    <select v-model="filterIsResolved" class="filter-input">
                        <option value="">{{ t('admin.errorLogs.filter.all') }}</option>
                        <option value="N">{{ t('admin.errorLogs.status.unresolved') }}</option>
                        <option value="Y">{{ t('admin.errorLogs.status.resolved') }}</option>
                    </select>
                </div>
                <div class="filter-item">
                    <label class="filter-label">{{ t('admin.errorLogs.filter.errorType') }}</label>
                    <input v-model="filterErrorType" type="text" class="filter-input"
                        placeholder="BusinessException..." />
                </div>
                <div class="filter-item filter-item--actions">
                    <div class="filter-actions-group">
                        <button @click="handleSearch" class="btn-search">
                            <Search class="w-4 h-4 mr-1" />
                            검색
                        </button>
                        <button @click="resetFilters" class="btn-reset">초기화</button>
                    </div>
                </div>
            </div>
        </div>

        <!-- 테이블 -->
        <BaseTable
            class="mt-4 error-log-table-wrapper"
            :columns="columns"
            :items="errorLogs"
            :loading="isLoading"
            :empty-text="'에러 로그가 없습니다.'"
            :row-class="getRowClass"
            row-key="errorLogId"
        >
            <template #loading>
                <div class="loading-indicator">
                    로딩 중...
                </div>
            </template>

            <template #cell-httpStatus="{ item: log }">
                <span class="http-status-badge" :class="getHttpStatusClass(log.httpStatus)">
                    {{ log.httpStatus }}
                </span>
            </template>

            <template #cell-errorCode="{ item: log }">
                <span class="text-xs font-mono">{{ log.errorCode || '-' }}</span>
            </template>

            <template #cell-errorType="{ item: log }">
                <span class="text-xs">{{ log.errorType }}</span>
            </template>

            <template #cell-message="{ item: log }">
                <span class="message-cell" :title="log.message">{{ log.message }}</span>
            </template>

            <template #cell-requestUri="{ item: log }">
                <span class="text-xs font-mono uri-cell" :title="log.requestUri">{{ log.requestUri }}</span>
            </template>

            <template #cell-ipAddress="{ item: log }">
                <span class="text-xs font-mono">{{ log.ipAddress }}</span>
            </template>

            <template #cell-isResolved="{ item: log }">
                <span class="resolve-badge"
                    :class="log.isResolved === 'Y' ? 'resolve-badge--resolved' : 'resolve-badge--unresolved'">
                    {{ log.isResolved === 'Y' ? t('admin.errorLogs.status.resolved') :
                        t('admin.errorLogs.status.unresolved') }}
                </span>
            </template>

            <template #cell-createdAt="{ item: log }">
                <span class="text-xs">{{ formatDateTimeOrDash(log.createdAt) }}</span>
            </template>

            <template #cell-actions="{ item: log }">
                <div class="action-buttons">
                    <button type="button" @click="openDetailModal(log)" class="btn-icon"
                        :title="t('admin.errorLogs.actions.viewDetail')"
                        :aria-label="t('admin.errorLogs.actions.viewDetail')">
                        <Eye class="w-4 h-4" aria-hidden="true" />
                    </button>
                    <button v-if="log.isResolved === 'N'" type="button" @click="openResolveModal(log)"
                        class="btn-icon btn-icon--resolve" :title="t('admin.errorLogs.actions.resolve')"
                        :aria-label="t('admin.errorLogs.actions.resolve')">
                        <CheckCircle class="w-4 h-4" aria-hidden="true" />
                    </button>
                </div>
            </template>
        </BaseTable>

        <!-- 페이징 -->
        <AdminPaginationFooter
            :page="page"
            :total-pages="totalPages"
            :summary="`총 ${totalElements}건 (${page + 1} / ${totalPages} 페이지)`"
            @page-change="page = $event"
        />

        <!-- 상세 모달 -->
        <Teleport to="body">
            <div v-if="isDetailModalOpen && selectedLog" class="modal-overlay" @click.self="closeDetailModal">
                <div class="modal-content modal-content--lg">
                    <div class="modal-header">
                        <h3>{{ t('admin.errorLogs.detail.title') }}</h3>
                        <button type="button" @click="closeDetailModal" class="btn-close" aria-label="상세 모달 닫기">
                            <X class="w-5 h-5" aria-hidden="true" />
                        </button>
                    </div>
                    <div class="modal-body">
                        <!-- 에러 정보 -->
                        <div class="detail-section">
                            <h4 class="detail-section-title">{{ t('admin.errorLogs.detail.errorInfo') }}</h4>
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.httpStatus') }}</span>
                                    <span class="http-status-badge" :class="getHttpStatusClass(selectedLog.httpStatus)">
                                        {{ selectedLog.httpStatus }}
                                    </span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.errorCode') }}</span>
                                    <span class="detail-value font-mono">{{ selectedLog.errorCode || '-' }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.errorType') }}</span>
                                    <span class="detail-value">{{ selectedLog.errorType }}</span>
                                </div>
                                <div class="detail-item detail-item--full">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.message') }}</span>
                                    <span class="detail-value">{{ selectedLog.message }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 요청 정보 -->
                        <div class="detail-section">
                            <h4 class="detail-section-title">{{ t('admin.errorLogs.detail.requestInfo') }}</h4>
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.requestMethod') }}</span>
                                    <span class="detail-value font-mono">{{ selectedLog.requestMethod }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.requestUri') }}</span>
                                    <span class="detail-value font-mono">{{ selectedLog.requestUri }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.userId') }}</span>
                                    <span class="detail-value">{{ selectedLog.userId || '-' }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.ipAddress') }}</span>
                                    <span class="detail-value font-mono">{{ selectedLog.ipAddress }}</span>
                                </div>
                                <div class="detail-item detail-item--full">
                                    <span class="detail-label">User-Agent</span>
                                    <span class="detail-value text-xs break-all">{{ selectedLog.userAgent || '-'
                                        }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">{{ t('admin.errorLogs.table.createdAt') }}</span>
                                    <span class="detail-value">{{ formatDateTimeOrDash(selectedLog.createdAt) }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 스택 트레이스 -->
                        <div v-if="selectedLog.stackTrace" class="detail-section">
                            <div class="stack-trace-header">
                                <h4 class="detail-section-title stack-trace-title">{{ t('admin.errorLogs.detail.stackTrace') }}</h4>
                                <button @click="copyStackTrace" type="button" class="btn-copy-stack-trace">
                                    <Copy class="w-3.5 h-3.5" />
                                    {{ t('admin.errorLogs.actions.copy') }}
                                </button>
                            </div>
                            <pre class="stack-trace-block">{{ selectedLog.stackTrace }}</pre>
                        </div>

                        <!-- 처리 정보 -->
                        <div v-if="selectedLog.isResolved === 'Y'" class="detail-section">
                            <h4 class="detail-section-title">{{ t('admin.errorLogs.detail.resolveInfo') }}</h4>
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <span class="detail-label">처리자 ID</span>
                                    <span class="detail-value">{{ selectedLog.resolvedBy }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">처리 일시</span>
                                    <span class="detail-value">{{ formatDateTimeOrDash(selectedLog.resolvedAt) }}</span>
                                </div>
                                <div v-if="selectedLog.resolvedMemo" class="detail-item detail-item--full">
                                    <span class="detail-label">처리 메모</span>
                                    <span class="detail-value">{{ selectedLog.resolvedMemo }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button v-if="selectedLog.isResolved === 'N'"
                            @click="openResolveModal(selectedLog); closeDetailModal()" class="btn-resolve">
                            <CheckCircle class="w-4 h-4 mr-1" />
                            {{ t('admin.errorLogs.actions.resolve') }}
                        </button>
                        <button @click="closeDetailModal" class="btn-cancel">닫기</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <!-- 확인 처리 모달 -->
        <Teleport to="body">
            <div v-if="isResolveModalOpen && resolveTargetLog" class="modal-overlay" @click.self="closeResolveModal">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3>{{ t('admin.errorLogs.actions.resolve') }}</h3>
                        <button type="button" @click="closeResolveModal" class="btn-close" aria-label="확인 처리 모달 닫기">
                            <X class="w-5 h-5" aria-hidden="true" />
                        </button>
                    </div>
                    <div class="modal-body">
                        <div class="resolve-info">
                            <p><strong>{{ t('admin.errorLogs.table.errorType') }}:</strong> {{
                                resolveTargetLog.errorType }}</p>
                            <p><strong>{{ t('admin.errorLogs.table.message') }}:</strong> {{ resolveTargetLog.message }}
                            </p>
                        </div>
                        <div class="mt-4">
                            <label class="filter-label">{{ t('admin.errorLogs.memoPlaceholder') }}</label>
                            <textarea v-model="resolveMemo" rows="3" class="filter-input w-full"
                                :placeholder="t('admin.errorLogs.memoPlaceholder')"></textarea>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button @click="handleResolve" class="btn-resolve">
                            <CheckCircle class="w-4 h-4 mr-1" />
                            {{ t('admin.errorLogs.actions.resolve') }}
                        </button>
                        <button @click="closeResolveModal" class="btn-cancel">{{ t('admin.sanction.cancel') }}</button>
                    </div>
                </div>
            </div>
        </Teleport>
    </div>
</template>

<style scoped>
/* 통계 카드 */
.error-log-stat-card {
    background: var(--nv-surface);
    border-radius: 8px;
    padding: 16px 20px;
    border: 1px solid var(--nv-border);
    box-shadow: var(--nv-shadow-card);
}

.error-log-stat-card--warning {
    border-left: 4px solid var(--nv-warning);
}

.error-log-stat-card--success {
    border-left: 4px solid var(--nv-success);
}

.stat-label {
    font-size: 0.75rem;
    color: var(--nv-text-subtle);
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.stat-value {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--nv-text);
    margin-top: 4px;
}

/* 필터 */
.error-log-filter-bar {
    background: var(--nv-surface);
    border-radius: 8px;
    padding: 16px;
    border: 1px solid var(--nv-border);
}

.filter-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 12px;
    align-items: end;
}

.filter-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.filter-item--actions {
    display: flex;
    justify-content: flex-end;
    align-items: flex-end;
}

.filter-actions-group {
    display: flex;
    gap: 6px;
}

.btn-search {
    display: inline-flex;
    align-items: center;
    padding: 6px 14px;
    border: none;
    border-radius: 6px;
    font-size: 0.8125rem;
    font-weight: 500;
    color: white;
    background: var(--nv-accent);
    cursor: pointer;
    transition: background 0.15s;
}

.btn-search:hover {
    background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.filter-label {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--nv-text-muted);
}

.filter-input {
    padding: 6px 10px;
    border: 1px solid var(--nv-border);
    border-radius: 6px;
    font-size: 0.8125rem;
    background: var(--nv-surface);
    color: var(--nv-text);
}

.btn-reset {
    padding: 6px 14px;
    border: 1px solid var(--nv-border);
    border-radius: 6px;
    font-size: 0.8125rem;
    color: var(--nv-text-muted);
    background: var(--nv-surface);
    cursor: pointer;
    transition: background 0.15s;
}

.btn-reset:hover {
    background: var(--nv-surface-hover);
    color: var(--nv-text);
}

/* 테이블 */
.error-log-table-wrapper {
    overflow-x: auto;
    background: var(--nv-surface);
    border-radius: 8px;
    border: 1px solid var(--nv-border);
}

.loading-indicator {
    text-align: center;
    padding: 40px;
    color: var(--nv-text-subtle);
}

.row-unresolved {
    background: var(--nv-warning-bg);
}

.message-cell {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.uri-cell {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 뱃지 */
.http-status-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 0.75rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
}

.status-500 {
    background: var(--nv-danger-bg);
    color: var(--nv-danger-text);
}

.status-400 {
    background: var(--nv-warning-bg);
    color: var(--nv-warning-text);
}

.status-other {
    background: var(--nv-success-bg);
    color: var(--nv-success-text);
}

.resolve-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 0.6875rem;
    font-weight: 600;
}

.resolve-badge--resolved {
    background: var(--nv-success-bg);
    color: var(--nv-success-text);
}

.resolve-badge--unresolved {
    background: var(--nv-danger-bg);
    color: var(--nv-danger-text);
}

/* 액션 버튼 */
.action-buttons {
    display: flex;
    gap: 4px;
}

.btn-icon {
    padding: 4px;
    border-radius: 4px;
    color: var(--nv-text-subtle);
    background: transparent;
    border: none;
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

/* 모달 */
.modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 50;
    padding: 16px;
}

.modal-content {
    background: var(--nv-surface);
    color: var(--nv-text);
    border-radius: 12px;
    width: 100%;
    max-width: 500px;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: var(--nv-shadow-popup);
}

.modal-content--lg {
    max-width: 720px;
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid var(--nv-border);
}

.modal-header h3 {
    font-size: 1rem;
    font-weight: 600;
    color: var(--nv-text);
}

.btn-close {
    padding: 4px;
    border-radius: 4px;
    color: var(--nv-text-muted);
    background: transparent;
    border: none;
    cursor: pointer;
}

.btn-close:hover {
    background: var(--nv-surface-hover);
}

.modal-body {
    padding: 20px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding: 16px 20px;
    border-top: 1px solid var(--nv-border);
}

/* 상세 섹션 */
.detail-section {
    margin-bottom: 20px;
}

.detail-section:last-child {
    margin-bottom: 0;
}

.detail-section-title {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--nv-text);
    margin-bottom: 12px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--nv-border);
}

.stack-trace-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.stack-trace-title {
    margin-bottom: 0;
    flex: 1;
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
    transition: background 0.15s;
}

.btn-copy-stack-trace:hover {
    background: var(--nv-surface-hover);
    color: var(--nv-text);
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
    font-size: 0.6875rem;
    font-weight: 500;
    color: var(--nv-text-subtle);
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.detail-value {
    font-size: 0.8125rem;
    color: var(--nv-text);
    word-break: break-all;
}

.stack-trace-block {
    background: #111827;
    color: #e5e7eb;
    padding: 12px 16px;
    border-radius: 6px;
    font-size: 0.6875rem;
    font-family: 'Fira Code', 'JetBrains Mono', monospace;
    line-height: 1.5;
    overflow-x: auto;
    max-height: 300px;
    white-space: pre-wrap;
    word-break: break-all;
}

.dark .stack-trace-block {
    background: #030712;
    color: #d1d5db;
}

.resolve-info p {
    margin: 4px 0;
    font-size: 0.875rem;
    color: var(--nv-text-muted);
}

/* 버튼 */
.btn-resolve {
    display: inline-flex;
    align-items: center;
    padding: 8px 16px;
    background: var(--nv-accent);
    color: white;
    border: none;
    border-radius: 6px;
    font-size: 0.8125rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-resolve:hover {
    background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.btn-cancel {
    padding: 8px 16px;
    background: transparent;
    color: var(--nv-text-muted);
    border: 1px solid var(--nv-border);
    border-radius: 6px;
    font-size: 0.8125rem;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-cancel:hover {
    background: var(--nv-surface-hover);
    color: var(--nv-text);
}
</style>
