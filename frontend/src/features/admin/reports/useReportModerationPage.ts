import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/features/admin/useAdmin'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import type { Report } from '@/types'
import type { ReportStatusFilter, ReportTargetTypeFilter } from '@/api/adminTypes'

interface SanctionTarget {
  id: number
  name: string
  sanctionContentId?: number
  sanctionContentType?: 'POST' | 'COMMENT' | 'USER'
  reportId: number
  modalRevision: number
  sessionGeneration: number
}

interface SanctionCompletedIntent {
  targetUserId: number
  reportId?: number
  modalRevision?: number
  sessionGeneration: number
}

export function useReportModerationPage() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const authStore = useAuthStore()
  const { confirmWithReason } = useConfirm()
  const { useReports, useResolveReport } = useAdmin()

  const statusFilter = ref<ReportStatusFilter | ''>('')
  const targetTypeFilter = ref<ReportTargetTypeFilter | ''>('')
  const reportFilters = computed(() => ({
    ...(statusFilter.value ? { status: statusFilter.value } : {}),
    ...(targetTypeFilter.value ? { targetType: targetTypeFilter.value } : {}),
  }))

  const {
    page,
    size,
    params,
    handlePageChange,
    handleSizeChange,
    resetPage,
    clampPageToTotalPages,
  } = usePaginatedQueryState({
    initialSize: 20,
    extraParams: reportFilters,
  })

  const { data: reportsData, isLoading } = useReports(params)
  const { mutateAsync: resolveReport } = useResolveReport()

  const {
    items: reports,
    totalPages,
    totalElements,
    currentPage,
  } = usePageResponseState(reportsData, page)

  const isModalOpen = ref(false)
  const selectedUser = ref<SanctionTarget | null>(null)
  const selectedSanctionReport = ref<Report | null>(null)
  const isDetailModalOpen = ref(false)
  const selectedReport = ref<Report | null>(null)
  let sanctionModalRevision = 0

  function openDetailModal(report: Report) {
    selectedReport.value = report
    isDetailModalOpen.value = true
  }

  function closeDetailModal() {
    isDetailModalOpen.value = false
    selectedReport.value = null
  }

  function openSanctionModal(report: Report) {
    const userId = report.targetUserId ?? (report.targetType === 'USER' ? report.targetId : null)
    if (userId == null) {
      return
    }

    sanctionModalRevision += 1
    selectedUser.value = {
      id: userId,
      name: report.targetDisplayName ?? t('notification.actors.unknown'),
      sanctionContentId: report.targetId,
      sanctionContentType: report.targetType,
      reportId: report.reportId,
      modalRevision: sanctionModalRevision,
      sessionGeneration: authStore.sessionGeneration,
    }
    selectedSanctionReport.value = report
    isModalOpen.value = true
  }

  function closeSanctionModal() {
    sanctionModalRevision += 1
    isModalOpen.value = false
    selectedSanctionReport.value = null
    selectedUser.value = null
  }

  function handleStatusFilterChange(value: string | number) {
    statusFilter.value = typeof value === 'string' ? value as ReportStatusFilter | '' : ''
    resetPage()
  }

  function handleTargetTypeFilterChange(value: string | number) {
    targetTypeFilter.value = typeof value === 'string' ? value as ReportTargetTypeFilter | '' : ''
    resetPage()
  }

  async function handleSanctioned(intent: SanctionCompletedIntent) {
    const report = selectedSanctionReport.value
    if (!report || report.status !== 'PENDING') return
    if (
      !isModalOpen.value
      || intent.sessionGeneration !== authStore.sessionGeneration
      || selectedUser.value?.sessionGeneration !== authStore.sessionGeneration
      || intent.reportId !== report.reportId
      || intent.targetUserId !== selectedUser.value?.id
      || intent.modalRevision !== selectedUser.value?.modalRevision
    ) return

    const sessionGeneration = intent.sessionGeneration
    const reportId = report.reportId

    try {
      await resolveReport({ reportId, data: { status: 'RESOLVED' } })
      if (sessionGeneration !== authStore.sessionGeneration) return
      toastStore.addToast(t('admin.reports.messages.sanctionResolved'), 'success')
    } catch {
      if (sessionGeneration === authStore.sessionGeneration) {
        toastStore.addToast(t('admin.reports.messages.sanctionResolveFailed'), 'error')
      }
    }
  }

  watch(totalPages, (nextTotalPages) => {
    clampPageToTotalPages(nextTotalPages)
  })

  watch(() => authStore.sessionGeneration, () => {
    closeDetailModal()
    closeSanctionModal()
  }, { flush: 'sync' })

  async function handleResolve(report: Report) {
    const sessionGeneration = authStore.sessionGeneration
    const remark = await confirmWithReason(
      t('admin.reports.messages.confirmResolve'),
      t('admin.reports.actions.resolve'),
      t('admin.reports.remark'),
      undefined,
      undefined,
      { maxLength: 255 },
    )
    if (!remark) return
    if (sessionGeneration !== authStore.sessionGeneration) return
    try {
      await resolveReport({ reportId: report.reportId, data: { status: 'RESOLVED', remark } })
      if (sessionGeneration !== authStore.sessionGeneration) return
      toastStore.addToast(t('admin.reports.messages.resolved'), 'success')
    } catch {
      // Error handled globally
    }
  }

  async function handleReject(report: Report) {
    const sessionGeneration = authStore.sessionGeneration
    const remark = await confirmWithReason(
      t('admin.reports.messages.confirmReject'),
      t('admin.reports.actions.reject'),
      t('admin.reports.remark'),
      undefined,
      undefined,
      { maxLength: 255 },
    )
    if (!remark) return
    if (sessionGeneration !== authStore.sessionGeneration) return
    try {
      await resolveReport({ reportId: report.reportId, data: { status: 'REJECTED', remark } })
      if (sessionGeneration !== authStore.sessionGeneration) return
      toastStore.addToast(t('admin.reports.messages.rejected'), 'success')
    } catch {
      // Error handled globally
    }
  }

  return {
    page,
    size,
    statusFilter,
    targetTypeFilter,
    reports,
    totalPages,
    totalElements,
    currentPage,
    isLoading,
    isModalOpen,
    selectedUser,
    isDetailModalOpen,
    selectedReport,
    handlePageChange,
    handleSizeChange,
    handleStatusFilterChange,
    handleTargetTypeFilterChange,
    openDetailModal,
    closeDetailModal,
    openSanctionModal,
    closeSanctionModal,
    handleSanctioned,
    handleResolve,
    handleReject,
  }
}
