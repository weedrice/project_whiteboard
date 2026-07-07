import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/composables/useAdmin'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { useToastStore } from '@/stores/toast'
import type { Report } from '@/types'

interface SanctionTarget {
  id: number
  name: string
  sanctionContentId?: number
  sanctionContentType?: 'POST' | 'COMMENT' | 'USER'
}

export function useReportModerationPage() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm } = useConfirm()
  const { useReports, useResolveReport } = useAdmin()

  const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({
    initialSize: 20,
  })

  const { data: reportsData, isLoading, refetch } = useReports(params)
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

  function openDetailModal(report: Report) {
    selectedReport.value = report
    isDetailModalOpen.value = true
  }

  function closeDetailModal() {
    isDetailModalOpen.value = false
  }

  function openSanctionModal(report: Report) {
    const userId = report.targetUserId ?? (report.targetType === 'USER' ? report.targetId : null)
    if (userId == null) {
      return
    }

    selectedUser.value = {
      id: userId,
      name: report.targetDisplayName ?? 'Unknown',
      sanctionContentId: report.targetId,
      sanctionContentType: report.targetType
    }
    selectedSanctionReport.value = report
    isModalOpen.value = true
  }

  function closeSanctionModal() {
    isModalOpen.value = false
    selectedSanctionReport.value = null
  }

  function refreshList() {
    refetch()
  }

  async function handleSanctioned() {
    const report = selectedSanctionReport.value
    if (!report || report.status !== 'PENDING') {
      refreshList()
      return
    }

    try {
      await resolveReport({ reportId: report.reportId, data: { status: 'RESOLVED' } })
      toastStore.addToast(t('admin.reports.messages.sanctionResolved'), 'success')
    } catch {
      toastStore.addToast(t('admin.reports.messages.sanctionResolveFailed'), 'error')
    } finally {
      refreshList()
    }
  }

  async function handleResolve(report: Report) {
    const isConfirmed = await confirm(t('admin.reports.messages.confirmResolve'))
    if (!isConfirmed) return
    try {
      await resolveReport({ reportId: report.reportId, data: { status: 'RESOLVED' } })
      toastStore.addToast(t('admin.reports.messages.resolved'), 'success')
    } catch {
      // Error handled globally
    }
  }

  async function handleReject(report: Report) {
    const isConfirmed = await confirm(t('admin.reports.messages.confirmReject'))
    if (!isConfirmed) return
    try {
      await resolveReport({ reportId: report.reportId, data: { status: 'REJECTED' } })
      toastStore.addToast(t('admin.reports.messages.rejected'), 'success')
    } catch {
      // Error handled globally
    }
  }

  return {
    page,
    size,
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
    openDetailModal,
    closeDetailModal,
    openSanctionModal,
    closeSanctionModal,
    refreshList,
    handleSanctioned,
    handleResolve,
    handleReject,
  }
}
