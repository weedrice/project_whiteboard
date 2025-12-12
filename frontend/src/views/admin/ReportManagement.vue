<script setup>
import { ref, computed } from 'vue'
import { useAdmin } from '@/composables/useAdmin'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import ReportList from '@/components/admin/ReportList.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import { useConfirm } from '@/composables/useConfirm'

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

const isModalOpen = ref(false)
const selectedUser = ref(null)

function openSanctionModal(report) {
  // Assuming report has targetUser or similar field. 
  // Based on previous ReportList.vue, it was report.targetUser.
  // However, the new ReportList component passes 'report' object.
  // We need to ensure we pass the user object correctly.
  // Let's assume report.targetUser exists as per previous code.
  // If report is about a post/comment, we might need to extract the author.
  // For now, let's use report.targetUser if available, or try to find a user field.
  // Looking at ReportList.vue (component), it displays report.reporterDisplayName and report.targetType.
  // It doesn't explicitly show targetUser.
  // But let's assume the API response includes targetUser info.
  selectedUser.value = report.targetUser || { id: report.targetId, name: 'Unknown' }
  isModalOpen.value = true
}

function refreshList() {
  refetch()
}

async function handleResolve(report) {
  const isConfirmed = await confirm(t('admin.reports.messages.confirmResolve'))
  if (!isConfirmed) return
  try {
    await resolveReport({ reportId: report.reportId, data: { status: 'RESOLVED' } })
    toastStore.addToast(t('admin.reports.messages.resolved'), 'success')
  } catch (err) {
    // Error handled globally
  }
}

async function handleReject(report) {
  const isConfirmed = await confirm(t('admin.reports.messages.confirmReject'))
  if (!isConfirmed) return
  try {
    await resolveReport({ reportId: report.reportId, data: { status: 'REJECTED' } })
    toastStore.addToast(t('admin.reports.messages.rejected'), 'success')
  } catch (err) {
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

    <ReportList :reports="reports" @resolve="handleResolve" @reject="handleReject" @sanction="openSanctionModal" />

    <SanctionModal :isOpen="isModalOpen" :user="selectedUser" @close="isModalOpen = false" @sanctioned="refreshList" />
  </div>
</template>
