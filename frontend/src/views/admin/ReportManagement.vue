<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import ReportList from '@/components/admin/ReportList.vue'
import ReportDetailModal from '@/components/admin/ReportDetailModal.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { useConfirm } from '@/composables/useConfirm'
import type { Report } from '@/types'

const { t } = useI18n()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useReports, useResolveReport } = useAdmin()

const page = ref(0)
const size = ref(20)
const params = computed(() => ({
  page: page.value,
  size: size.value
}))

const { data: reportsData, isLoading, refetch } = useReports(params)
const { mutateAsync: resolveReport } = useResolveReport()

const reports = computed(() => reportsData.value?.content || [])
const totalPages = computed(() => reportsData.value?.totalPages || 0)
const totalElements = computed(() => reportsData.value?.totalElements || 0)
const currentPage = computed(() => reportsData.value?.number ?? page.value)

function handlePageChange(nextPage: number) {
  page.value = nextPage
}

function handleSizeChange() {
  page.value = 0
}

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
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">{{ t('admin.reports.title') }}</h1>
        <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ t('admin.reports.description') }}</p>
      </div>
    </div>

    <div class="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p class="text-sm text-gray-600 dark:text-gray-300">총 {{ totalElements }}건</p>
      <div class="flex items-center gap-3">
        <span v-if="isLoading" class="text-xs text-gray-500 dark:text-gray-400">{{ t('common.loading') }}</span>
        <PageSizeSelector v-model="size" :options="[20, 50, 100]" @change="handleSizeChange" />
      </div>
    </div>

    <ReportList :reports="reports" @resolve="handleResolve" @reject="handleReject" @sanction="openSanctionModal"
      @viewDetail="openDetailModal" />

    <div class="mt-4">
      <Pagination :current-page="currentPage" :total-pages="totalPages" @page-change="handlePageChange" />
    </div>

    <ReportDetailModal :isOpen="isDetailModalOpen" :report="selectedReport" @close="isDetailModalOpen = false" />

    <SanctionModal v-if="selectedUser" :isOpen="isModalOpen" :user="selectedUser" @close="isModalOpen = false"
      @sanctioned="refreshList" />
  </div>
</template>
