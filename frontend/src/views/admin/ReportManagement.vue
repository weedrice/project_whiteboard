<script setup lang="ts">
import { ref } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import ReportList from '@/components/admin/ReportList.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import type { Report } from '@/types'

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
const selectedUser = ref<{
  id: number
  name: string
  sanctionContentId?: number
  sanctionContentType?: 'POST' | 'COMMENT' | 'USER'
} | null>(null)

const isDetailModalOpen = ref(false)
const selectedReport = ref<Report | null>(null)

function openDetailModal(report: Report) {
  selectedReport.value = report
  isDetailModalOpen.value = true
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
  isModalOpen.value = true
}

function refreshList() {
  refetch()
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
</script>

<template>
  <div>
    <AdminPageHeader :title="t('admin.reports.title')" :description="t('admin.reports.description')" />

    <div class="mt-6 flex justify-end">
      <div class="flex items-center gap-3">
        <span v-if="isLoading" class="text-xs text-gray-500 dark:text-gray-400">{{ t('common.loading') }}</span>
        <PageSizeSelector v-model="size" :options="[20, 50, 100]" @change="handleSizeChange" />
      </div>
    </div>

    <ReportList :reports="reports" @resolve="handleResolve" @reject="handleReject" @sanction="openSanctionModal"
      @viewDetail="openDetailModal" />

    <AdminPaginationFooter
      :page="currentPage"
      :total-pages="totalPages"
      :summary="`총 ${totalElements}건`"
      :loading-text="t('common.loading')"
      @page-change="handlePageChange"
    />

    <ReportDetailModal :isOpen="isDetailModalOpen" :report="selectedReport" @close="isDetailModalOpen = false" />

    <SanctionModal v-if="selectedUser" :isOpen="isModalOpen" :user="selectedUser" @close="isModalOpen = false"
      @sanctioned="refreshList" />
  </div>
</template>
