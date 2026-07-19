import { computed, reactive, ref, watch, type Ref } from 'vue'
import { boardApi } from '@/api/board'
import type {
  ReportSearchParams,
  ReportStatusFilter,
  ReportTargetTypeFilter,
} from '@/api/adminTypes'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import type { ModerationAuditLog, ModerationAuditSearchParams, Report } from '@/types'

export interface BoardManagerAuditFilterForm {
  action: string
  actorType: string
  actorUserId: string
  startDate: string
  endDate: string
}

const createAuditFilters = (): BoardManagerAuditFilterForm => ({
  action: '',
  actorType: '',
  actorUserId: '',
  startDate: '',
  endDate: '',
})

function toPositiveInteger(value: string) {
  if (!/^\d+$/.test(value)) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

export function useBoardManagerGovernance(boardUrl: Ref<string>, enabled: Ref<boolean>) {
  const reportStatus = ref<ReportStatusFilter | ''>('')
  const reportTargetType = ref<ReportTargetTypeFilter | ''>('')
  const reportFilters = computed(() => ({
    ...(reportStatus.value ? { status: reportStatus.value } : {}),
    ...(reportTargetType.value ? { targetType: reportTargetType.value } : {}),
  }))
  const reportPagination = usePaginatedQueryState({
    initialSize: 20,
    extraParams: reportFilters,
  })
  const reportQuery = useApiPageQuery<Report>({
    queryKey: computed(() => boardQueryKeys.managerReports(boardUrl.value, reportPagination.params.value)),
    request: ({ signal }) => boardApi.getManagerReports(
      boardUrl.value,
      reportPagination.params.value as ReportSearchParams,
      { signal },
    ),
    enabled,
    retry: false,
    meta: AUTH_SCOPED_QUERY_META,
  })
  const reportPage = usePageResponseState(reportQuery.data, reportPagination.page)

  const auditFilterForm = reactive<BoardManagerAuditFilterForm>(createAuditFilters())
  const appliedAuditFilters = ref<BoardManagerAuditFilterForm>(createAuditFilters())
  const auditFilters = computed<Record<string, unknown>>(() => ({
    ...(appliedAuditFilters.value.action.trim()
      ? { action: appliedAuditFilters.value.action.trim() }
      : {}),
    ...(appliedAuditFilters.value.actorType
      ? { actorType: appliedAuditFilters.value.actorType }
      : {}),
    ...(toPositiveInteger(appliedAuditFilters.value.actorUserId) != null
      ? { actorUserId: toPositiveInteger(appliedAuditFilters.value.actorUserId) }
      : {}),
    ...(appliedAuditFilters.value.startDate
      ? { startDate: appliedAuditFilters.value.startDate }
      : {}),
    ...(appliedAuditFilters.value.endDate
      ? { endDate: appliedAuditFilters.value.endDate }
      : {}),
    sort: 'createdAt,desc',
  }))
  const auditPagination = usePaginatedQueryState({
    initialSize: 20,
    extraParams: auditFilters,
  })
  const auditQuery = useApiPageQuery<ModerationAuditLog>({
    queryKey: computed(() => boardQueryKeys.managerAudits(boardUrl.value, auditPagination.params.value)),
    request: ({ signal }) => boardApi.getManagerAudits(
      boardUrl.value,
      auditPagination.params.value as ModerationAuditSearchParams,
      { signal },
    ),
    enabled,
    retry: false,
    meta: AUTH_SCOPED_QUERY_META,
  })
  const auditPage = usePageResponseState(auditQuery.data, auditPagination.page)

  function handleReportStatusChange(value: string | number) {
    reportStatus.value = typeof value === 'string' ? value as ReportStatusFilter | '' : ''
    reportPagination.resetPage()
  }

  function handleReportTargetTypeChange(value: string | number) {
    reportTargetType.value = typeof value === 'string' ? value as ReportTargetTypeFilter | '' : ''
    reportPagination.resetPage()
  }

  function applyAuditFilters() {
    appliedAuditFilters.value = { ...auditFilterForm }
    auditPagination.resetPage()
  }

  function resetAuditFilters() {
    Object.assign(auditFilterForm, createAuditFilters())
    appliedAuditFilters.value = createAuditFilters()
    auditPagination.resetPage()
  }

  watch(boardUrl, () => {
    reportPagination.resetPage()
    auditPagination.resetPage()
  })
  watch(() => reportQuery.data.value?.totalPages, (totalPages) => {
    if (totalPages != null) reportPagination.clampPageToTotalPages(totalPages)
  })
  watch(() => auditQuery.data.value?.totalPages, (totalPages) => {
    if (totalPages != null) auditPagination.clampPageToTotalPages(totalPages)
  })

  return {
    reportStatus,
    reportTargetType,
    reportPagination,
    reportQuery,
    reportPage,
    handleReportStatusChange,
    handleReportTargetTypeChange,
    auditFilterForm,
    appliedAuditFilters,
    auditPagination,
    auditQuery,
    auditPage,
    applyAuditFilters,
    resetAuditFilters,
  }
}
