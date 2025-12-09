<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white">Report Management</h2>
    </div>

    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="reports.length === 0" class="text-center py-8 bg-white dark:bg-gray-800 shadow rounded-lg border border-gray-200 dark:border-gray-700">
      <p class="text-gray-500 dark:text-gray-400">No pending reports.</p>
    </div>

    <div v-else class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-md border border-gray-200 dark:border-gray-700">
      <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="report in reports" :key="report.id" class="px-4 py-4 sm:px-6">
          <div class="flex items-center justify-between">
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                {{ report.type }} #{{ report.targetId }}
              </p>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                Reported by: {{ report.reporterName }}
              </p>
              <p class="mt-1 text-sm text-gray-900 dark:text-white">
                Reason: {{ report.reason }}
              </p>
              <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                {{ new Date(report.createdAt).toLocaleString() }}
              </p>
            </div>
            <div class="ml-4 flex-shrink-0 flex space-x-2">
              <BaseButton size="sm" @click="openSanctionModal(report.targetUser)">
                Sanction User
              </BaseButton>
              <BaseButton size="sm" variant="secondary" @click="dismissReport(report.id)">
                Dismiss
              </BaseButton>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <SanctionModal
      :isOpen="isModalOpen"
      :user="selectedUser"
      @close="isModalOpen = false"
      @sanctioned="refreshList"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import { useAdmin } from '@/composables/useAdmin'
import logger from '@/utils/logger'

const { useReports, useResolveReport } = useAdmin()

const page = ref(0)
const size = ref(20)
const params = computed(() => ({
    page: page.value,
    size: size.value
}))

const { data: reportsData, isLoading: loading } = useReports(params)
const { mutateAsync: resolveReport } = useResolveReport()

const reports = computed(() => reportsData.value?.content || [])

const isModalOpen = ref(false)
const selectedUser = ref(null)

const openSanctionModal = (user) => {
  selectedUser.value = user
  isModalOpen.value = true
}

const dismissReport = async (id) => {
  if (!confirm('Dismiss this report?')) return
  
  try {
    await resolveReport({ reportId: id, data: { status: 'DISMISSED' } })
  } catch (error) {
    logger.error('Failed to dismiss report:', error)
  }
}

const refreshList = () => {
    // Query invalidation handles refresh automatically
}
</script>
