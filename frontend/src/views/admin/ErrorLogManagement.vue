<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { Eye, CheckCircle, ChevronLeft, ChevronRight, X, Search } from 'lucide-vue-next'
import type { ErrorLogItem } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { useErrorLogs, useResolveErrorLog, useErrorLogStats } = useAdmin()

// 날짜 기본값 (시작일: 2주 전, 종료일: 오늘)
function toDateString(date: Date): string {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
}

const today = new Date()
const twoWeeksAgo = new Date(today)
twoWeeksAgo.setDate(today.getDate() - 14)

const defaultStartDate = toDateString(twoWeeksAgo)
const defaultEndDate = toDateString(today)

// 검색 조건 (입력용)
const page = ref(0)
const size = ref(20)
const filterErrorType = ref('')
const filterHttpStatus = ref<number | undefined>(undefined)
const filterIsResolved = ref('')
const filterStartDate = ref(defaultStartDate)
const filterEndDate = ref(defaultEndDate)

// 실제 검색에 사용되는 파라미터 (검색 버튼 클릭 시 반영)
const searchParams = ref({
    page: 0,
    size: 20,
    errorType: undefined as string | undefined,
    httpStatus: undefined as number | undefined,
    isResolved: undefined as string | undefined,
    startDate: defaultStartDate as string | undefined,
    endDate: defaultEndDate as string | undefined,
})

const params = computed(() => ({
    ...searchParams.value,
    page: page.value,
}))

function handleSearch() {
    searchParams.value = {
        page: 0,
        size: size.value,
        errorType: filterErrorType.value || undefined,
        httpStatus: filterHttpStatus.value || undefined,
        isResolved: filterIsResolved.value || undefined,
        startDate: filterStartDate.value || undefined,
        endDate: filterEndDate.value || undefined,
    }
    page.value = 0
}

const { data: errorLogsData, isLoading, refetch } = useErrorLogs(params)
const { data: statsData } = useErrorLogStats()
const { mutateAsync: resolveErrorLog } = useResolveErrorLog()

const errorLogs = computed(() => errorLogsData.value?.content || [])
const totalPages = computed(() => errorLogsData.value?.totalPages || 0)
const totalElements = computed(() => errorLogsData.value?.totalElements || 0)

// 상세 모달
const isDetailModalOpen = ref(false)
const selectedLog = ref<ErrorLogItem | null>(null)

// 확인 처리 모달
const isResolveModalOpen = ref(false)
const resolveTargetLog = ref<ErrorLogItem | null>(null)
const resolveMemo = ref('')

function openDetailModal(log: ErrorLogItem) {
    selectedLog.value = log
    isDetailModalOpen.value = true
}

function closeDetailModal() {
    isDetailModalOpen.value = false
    selectedLog.value = null
}

function openResolveModal(log: ErrorLogItem) {
    resolveTargetLog.value = log
    resolveMemo.value = ''
    isResolveModalOpen.value = true
}

function closeResolveModal() {
    isResolveModalOpen.value = false
    resolveTargetLog.value = null
    resolveMemo.value = ''
}

async function handleResolve() {
    if (!resolveTargetLog.value) return
    try {
        await resolveErrorLog({
            errorLogId: resolveTargetLog.value.errorLogId,
            data: resolveMemo.value ? { memo: resolveMemo.value } : undefined
        })
        toastStore.addToast(t('admin.errorLogs.messages.resolved'), 'success')
        closeResolveModal()
        refetch()
    } catch {
        // Error handled globally
    }
}

function getHttpStatusClass(status: number): string {
    if (status >= 500) return 'status-500'
    if (status >= 400) return 'status-400'
    return 'status-other'
}

function formatDate(dateStr: string): string {
    if (!dateStr) return '-'
    const date = new Date(dateStr)
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    })
}

function resetFilters() {
    filterErrorType.value = ''
    filterHttpStatus.value = undefined
    filterIsResolved.value = ''
    filterStartDate.value = defaultStartDate
    filterEndDate.value = defaultEndDate
    page.value = 0
    handleSearch()
}
</script>

<template>
    <div>
        <!-- 헤더 -->
        <div class="sm:flex sm:items-center">
            <div class="sm:flex-auto">
                <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.errorLogs.title') }}</h1>
                <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.errorLogs.description') }}</p>
            </div>
        </div>

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
        <div class="mt-4 error-log-table-wrapper">
            <div v-if="isLoading" class="loading-indicator">
                로딩 중...
            </div>
            <table v-else class="error-log-table">
                <thead>
                    <tr>
                        <th>{{ t('admin.errorLogs.table.httpStatus') }}</th>
                        <th>{{ t('admin.errorLogs.table.errorCode') }}</th>
                        <th>{{ t('admin.errorLogs.table.errorType') }}</th>
                        <th>{{ t('admin.errorLogs.table.message') }}</th>
                        <th>{{ t('admin.errorLogs.table.requestUri') }}</th>
                        <th>{{ t('admin.errorLogs.table.ipAddress') }}</th>
                        <th>{{ t('admin.errorLogs.table.isResolved') }}</th>
                        <th>{{ t('admin.errorLogs.table.createdAt') }}</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="log in errorLogs" :key="log.errorLogId"
                        :class="{ 'row-unresolved': log.isResolved === 'N' }">
                        <td>
                            <span class="http-status-badge" :class="getHttpStatusClass(log.httpStatus)">
                                {{ log.httpStatus }}
                            </span>
                        </td>
                        <td class="text-xs font-mono">{{ log.errorCode || '-' }}</td>
                        <td class="text-xs">{{ log.errorType }}</td>
                        <td class="message-cell" :title="log.message">{{ log.message }}</td>
                        <td class="text-xs font-mono uri-cell" :title="log.requestUri">{{ log.requestUri }}</td>
                        <td class="text-xs font-mono">{{ log.ipAddress }}</td>
                        <td>
                            <span class="resolve-badge"
                                :class="log.isResolved === 'Y' ? 'resolve-badge--resolved' : 'resolve-badge--unresolved'">
                                {{ log.isResolved === 'Y' ? t('admin.errorLogs.status.resolved') :
                                    t('admin.errorLogs.status.unresolved') }}
                            </span>
                        </td>
                        <td class="text-xs">{{ formatDate(log.createdAt) }}</td>
                        <td>
                            <div class="action-buttons">
                                <button @click="openDetailModal(log)" class="btn-icon"
                                    :title="t('admin.errorLogs.actions.viewDetail')">
                                    <Eye class="w-4 h-4" />
                                </button>
                                <button v-if="log.isResolved === 'N'" @click="openResolveModal(log)"
                                    class="btn-icon btn-icon--resolve" :title="t('admin.errorLogs.actions.resolve')">
                                    <CheckCircle class="w-4 h-4" />
                                </button>
                            </div>
                        </td>
                    </tr>
                    <tr v-if="errorLogs.length === 0">
                        <td colspan="9" class="empty-row">에러 로그가 없습니다.</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <!-- 페이징 -->
        <div v-if="totalPages > 0" class="mt-4 pagination-bar">
            <div class="pagination-info">
                총 {{ totalElements }}건 ({{ page + 1 }} / {{ totalPages }} 페이지)
            </div>
            <div class="pagination-buttons">
                <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-page">
                    <ChevronLeft class="w-4 h-4" />
                </button>
                <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1"
                    class="btn-page">
                    <ChevronRight class="w-4 h-4" />
                </button>
            </div>
        </div>

        <!-- 상세 모달 -->
        <Teleport to="body">
            <div v-if="isDetailModalOpen && selectedLog" class="modal-overlay" @click.self="closeDetailModal">
                <div class="modal-content modal-content--lg">
                    <div class="modal-header">
                        <h3>{{ t('admin.errorLogs.detail.title') }}</h3>
                        <button @click="closeDetailModal" class="btn-close">
                            <X class="w-5 h-5" />
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
                                    <span class="detail-value">{{ formatDate(selectedLog.createdAt) }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 스택 트레이스 -->
                        <div v-if="selectedLog.stackTrace" class="detail-section">
                            <h4 class="detail-section-title">{{ t('admin.errorLogs.detail.stackTrace') }}</h4>
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
                                    <span class="detail-value">{{ formatDate(selectedLog.resolvedAt || '') }}</span>
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
                        <button @click="closeResolveModal" class="btn-close">
                            <X class="w-5 h-5" />
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
    background: white;
    border-radius: 8px;
    padding: 16px 20px;
    border: 1px solid #e5e7eb;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.dark .error-log-stat-card {
    background: #1f2937;
    border-color: #374151;
}

.error-log-stat-card--warning {
    border-left: 4px solid #f59e0b;
}

.error-log-stat-card--success {
    border-left: 4px solid #10b981;
}

.stat-label {
    font-size: 0.75rem;
    color: #6b7280;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.dark .stat-label {
    color: #9ca3af;
}

.stat-value {
    font-size: 1.5rem;
    font-weight: 700;
    color: #111827;
    margin-top: 4px;
}

.dark .stat-value {
    color: #f9fafb;
}

/* 필터 */
.error-log-filter-bar {
    background: white;
    border-radius: 8px;
    padding: 16px;
    border: 1px solid #e5e7eb;
}

.dark .error-log-filter-bar {
    background: #1f2937;
    border-color: #374151;
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
    background: #4f46e5;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-search:hover {
    background: #4338ca;
}

.filter-label {
    font-size: 0.75rem;
    font-weight: 500;
    color: #374151;
}

.dark .filter-label {
    color: #d1d5db;
}

.filter-input {
    padding: 6px 10px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    font-size: 0.8125rem;
    background: white;
    color: #111827;
}

.dark .filter-input {
    background: #111827;
    border-color: #4b5563;
    color: #f9fafb;
}

.btn-reset {
    padding: 6px 14px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    font-size: 0.8125rem;
    color: #374151;
    background: white;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-reset:hover {
    background: #f3f4f6;
}

.dark .btn-reset {
    background: #374151;
    border-color: #4b5563;
    color: #d1d5db;
}

.dark .btn-reset:hover {
    background: #4b5563;
}

/* 테이블 */
.error-log-table-wrapper {
    overflow-x: auto;
    background: white;
    border-radius: 8px;
    border: 1px solid #e5e7eb;
}

.dark .error-log-table-wrapper {
    background: #1f2937;
    border-color: #374151;
}

.loading-indicator {
    text-align: center;
    padding: 40px;
    color: #6b7280;
}

.error-log-table {
    width: 100%;
    border-collapse: collapse;
}

.error-log-table thead th {
    padding: 10px 12px;
    font-size: 0.75rem;
    font-weight: 600;
    text-align: left;
    color: #6b7280;
    border-bottom: 1px solid #e5e7eb;
    white-space: nowrap;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.dark .error-log-table thead th {
    color: #9ca3af;
    border-bottom-color: #374151;
}

.error-log-table tbody td {
    padding: 8px 12px;
    font-size: 0.8125rem;
    color: #374151;
    border-bottom: 1px solid #f3f4f6;
    vertical-align: middle;
}

.dark .error-log-table tbody td {
    color: #d1d5db;
    border-bottom-color: #1f2937;
}

.error-log-table tbody tr:hover {
    background: #f9fafb;
}

.dark .error-log-table tbody tr:hover {
    background: #111827;
}

.row-unresolved {
    background: #fffbeb;
}

.dark .row-unresolved {
    background: rgba(245, 158, 11, 0.05);
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
    background: #fef2f2;
    color: #dc2626;
}

.dark .status-500 {
    background: rgba(220, 38, 38, 0.15);
    color: #fca5a5;
}

.status-400 {
    background: #fffbeb;
    color: #d97706;
}

.dark .status-400 {
    background: rgba(217, 119, 6, 0.15);
    color: #fcd34d;
}

.status-other {
    background: #f0fdf4;
    color: #16a34a;
}

.dark .status-other {
    background: rgba(22, 163, 74, 0.15);
    color: #86efac;
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
    background: #f0fdf4;
    color: #16a34a;
}

.dark .resolve-badge--resolved {
    background: rgba(22, 163, 74, 0.15);
    color: #86efac;
}

.resolve-badge--unresolved {
    background: #fef2f2;
    color: #dc2626;
}

.dark .resolve-badge--unresolved {
    background: rgba(220, 38, 38, 0.15);
    color: #fca5a5;
}

/* 액션 버튼 */
.action-buttons {
    display: flex;
    gap: 4px;
}

.btn-icon {
    padding: 4px;
    border-radius: 4px;
    color: #6b7280;
    background: transparent;
    border: none;
    cursor: pointer;
    transition: all 0.15s;
}

.btn-icon:hover {
    background: #f3f4f6;
    color: #111827;
}

.dark .btn-icon:hover {
    background: #374151;
    color: #f9fafb;
}

.btn-icon--resolve {
    color: #16a34a;
}

.btn-icon--resolve:hover {
    background: #f0fdf4;
    color: #16a34a;
}

.dark .btn-icon--resolve:hover {
    background: rgba(22, 163, 74, 0.15);
    color: #86efac;
}

.empty-row {
    text-align: center;
    padding: 40px 12px !important;
    color: #9ca3af;
}

/* 페이징 */
.pagination-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.pagination-info {
    font-size: 0.8125rem;
    color: #6b7280;
}

.dark .pagination-info {
    color: #9ca3af;
}

.pagination-buttons {
    display: flex;
    gap: 4px;
}

.btn-page {
    padding: 6px 10px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    background: white;
    color: #374151;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-page:hover:not(:disabled) {
    background: #f3f4f6;
}

.btn-page:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.dark .btn-page {
    background: #374151;
    border-color: #4b5563;
    color: #d1d5db;
}

.dark .btn-page:hover:not(:disabled) {
    background: #4b5563;
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
    background: white;
    border-radius: 12px;
    width: 100%;
    max-width: 500px;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

.modal-content--lg {
    max-width: 720px;
}

.dark .modal-content {
    background: #1f2937;
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid #e5e7eb;
}

.dark .modal-header {
    border-bottom-color: #374151;
}

.modal-header h3 {
    font-size: 1rem;
    font-weight: 600;
    color: #111827;
}

.dark .modal-header h3 {
    color: #f9fafb;
}

.btn-close {
    padding: 4px;
    border-radius: 4px;
    color: #6b7280;
    background: transparent;
    border: none;
    cursor: pointer;
}

.btn-close:hover {
    background: #f3f4f6;
}

.dark .btn-close:hover {
    background: #374151;
}

.modal-body {
    padding: 20px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding: 16px 20px;
    border-top: 1px solid #e5e7eb;
}

.dark .modal-footer {
    border-top-color: #374151;
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
    color: #111827;
    margin-bottom: 12px;
    padding-bottom: 6px;
    border-bottom: 1px solid #e5e7eb;
}

.dark .detail-section-title {
    color: #f9fafb;
    border-bottom-color: #374151;
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
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.dark .detail-label {
    color: #9ca3af;
}

.detail-value {
    font-size: 0.8125rem;
    color: #111827;
    word-break: break-all;
}

.dark .detail-value {
    color: #f9fafb;
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
    color: #374151;
}

.dark .resolve-info p {
    color: #d1d5db;
}

/* 버튼 */
.btn-resolve {
    display: inline-flex;
    align-items: center;
    padding: 8px 16px;
    background: #4f46e5;
    color: white;
    border: none;
    border-radius: 6px;
    font-size: 0.8125rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-resolve:hover {
    background: #4338ca;
}

.btn-cancel {
    padding: 8px 16px;
    background: transparent;
    color: #374151;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    font-size: 0.8125rem;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-cancel:hover {
    background: #f3f4f6;
}

.dark .btn-cancel {
    color: #d1d5db;
    border-color: #4b5563;
}

.dark .btn-cancel:hover {
    background: #374151;
}
</style>
