<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import ReportList from '@/components/admin/ReportList.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { useReportModerationPage } from '@/composables/useReportModerationPage'

const { t } = useI18n()
const {
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
  handleResolve,
  handleReject,
} = useReportModerationPage()
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

    <ReportDetailModal :isOpen="isDetailModalOpen" :report="selectedReport" @close="closeDetailModal" />

    <SanctionModal v-if="selectedUser" :isOpen="isModalOpen" :user="selectedUser" @close="closeSanctionModal"
      @sanctioned="refreshList" />
  </div>
</template>
