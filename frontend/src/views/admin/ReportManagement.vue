<script setup lang="ts">
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
              :options="[
                { value: '', label: t('admin.common.all') },
                { value: 'PENDING', label: t('admin.reports.status.PENDING') },
                { value: 'RESOLVED', label: t('admin.reports.status.RESOLVED') },
                { value: 'REJECTED', label: t('admin.reports.status.REJECTED') },
              ]"
              @update:model-value="handleStatusFilterChange"
            />
          </AdminFilterField>
          <AdminFilterField :label="t('admin.reports.targetType')" for-id="admin-report-target-filter" width="select">
            <BaseSelect
              id="admin-report-target-filter"
              :model-value="targetTypeFilter"
              :options="[
                { value: '', label: t('admin.common.all') },
                { value: 'POST', label: t('admin.dashboard.auditTargets.POST') },
                { value: 'COMMENT', label: t('admin.dashboard.auditTargets.COMMENT') },
                { value: 'USER', label: t('admin.dashboard.auditTargets.USER') },
              ]"
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
