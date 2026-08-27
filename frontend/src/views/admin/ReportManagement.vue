<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import ReportList from '@/components/admin/ReportList.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import { useReportModerationPage } from '@/features/admin/reports/useReportModerationPage'
import { formatAdminPaginationSummary } from '@/utils/adminPaginationSummary'
import { COMMON_CODE_TYPES, useSupportedCommonCodeValues } from '@/composables/useCommonCodeDetails'
import type { ReportStatusFilter, ReportTargetTypeFilter } from '@/api/adminTypes'

const { t } = useI18n()
const {
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
} = useReportModerationPage()

const SUPPORTED_REPORT_STATUSES: ReportStatusFilter[] = ['PENDING', 'RESOLVED', 'REJECTED']
const SUPPORTED_REPORT_TARGET_TYPES: ReportTargetTypeFilter[] = ['POST', 'COMMENT', 'USER']
const activeReportStatuses = useSupportedCommonCodeValues(
  COMMON_CODE_TYPES.REPORT_STATUS,
  SUPPORTED_REPORT_STATUSES,
)
const activeReportTargetTypes = useSupportedCommonCodeValues(
  COMMON_CODE_TYPES.TARGET_TYPE,
  SUPPORTED_REPORT_TARGET_TYPES,
)
const reportStatusOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...activeReportStatuses.value.map((value) => ({
    value,
    label: t(`admin.reports.status.${value}`),
  })),
])
const reportTargetTypeOptions = computed(() => [
  { value: '', label: t('admin.common.all') },
  ...activeReportTargetTypes.value.map((value) => ({
    value,
    label: t(`admin.dashboard.auditTargets.${value}`),
  })),
])

watch(activeReportStatuses, (statuses) => {
  if (statusFilter.value && !statuses.includes(statusFilter.value)) {
    handleStatusFilterChange('')
  }
})
watch(activeReportTargetTypes, (targetTypes) => {
  if (targetTypeFilter.value && !targetTypes.includes(targetTypeFilter.value)) {
    handleTargetTypeFilterChange('')
  }
})
</script>

<template>
  <AdminDataPage :title="t('admin.reports.title')" :description="t('admin.reports.description')">
    <template #toolbar>
      <div class="mt-6 flex flex-wrap items-end justify-between gap-3">
        <div class="flex flex-wrap items-end gap-3">
          <AdminFilterField :label="t('admin.reports.statusFilter')" for-id="admin-report-status-filter" width="select">
            <BaseSelect
              id="admin-report-status-filter"
              :model-value="statusFilter"
              :options="reportStatusOptions"
              @update:model-value="handleStatusFilterChange"
            />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.reports.targetType')" for-id="admin-report-target-filter" width="select">
            <BaseSelect
              id="admin-report-target-filter"
              :model-value="targetTypeFilter"
              :options="reportTargetTypeOptions"
              @update:model-value="handleTargetTypeFilterChange"
            />
          </AdminFilterField>
        </div>
        <div class="flex items-center gap-3">
          <span v-if="isLoading" class="text-xs nv-text-subtle">{{ t('common.loading') }}</span>
          <PageSizeSelector v-model="size" :options="[20, 50, 100]" @change="handleSizeChange" />
        </div>
      </div>
    </template>

    <ReportList
      :reports="reports"
      @resolve="handleResolve"
      @reject="handleReject"
      @sanction="openSanctionModal"
      @viewDetail="openDetailModal"
    />

    <template #footer>
      <AdminPaginationFooter
        :page="currentPage"
        :total-pages="totalPages"
        :summary="formatAdminPaginationSummary(totalElements, { t })"
        :loading-text="t('common.loading')"
        @page-change="handlePageChange"
      />
    </template>

    <ReportDetailModal :isOpen="isDetailModalOpen" :report="selectedReport" @close="closeDetailModal" />

    <SanctionModal
      v-if="selectedUser"
      :isOpen="isModalOpen"
      :user="selectedUser"
      @close="closeSanctionModal"
      @sanctioned="handleSanctioned"
    />
  </AdminDataPage>
</template>
