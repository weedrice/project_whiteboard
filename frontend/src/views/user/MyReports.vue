<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { reportApi } from '@/api/report'
import { formatDate } from '@/utils/date'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { Flag } from 'lucide-vue-next'
import logger from '@/utils/logger'
import type { Report } from '@/types'

const reports = ref<Report[]>([])
const loading = ref(false)
const page = ref(0)
const size = ref(15)
const totalPages = ref(0)

const fetchReports = async () => {
  loading.value = true
  try {
    const { data } = await reportApi.getMyReports({
      page: page.value,
      size: size.value
    })
    if (data.success) {
      reports.value = data.data.content
      totalPages.value = data.data.totalPages
    }
  } catch (error: unknown) {
    logger.error('Failed to load reports:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage: number) => {
  page.value = newPage
  fetchReports()
}

const handleSizeChange = () => {
  page.value = 0
  fetchReports()
}

onMounted(() => {
  fetchReports()
})
</script>

<template>
  <div class="max-w-4xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <Flag class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ $t('user.tabs.reports') }}
        </h3>
        <div class="hidden sm:block">
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
        </div>
      </div>
      <div v-if="loading && reports.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div class="flex flex-col flex-1">
            <BaseSkeleton width="60%" height="20px" className="mb-1" />
            <BaseSkeleton width="30%" height="14px" />
          </div>
          <BaseSkeleton width="60px" height="24px" rounded="rounded-full" />
        </div>
      </div>
      <EmptyState v-else-if="reports.length === 0" :title="$t('user.reportList.empty')" :icon="Flag" />
      <ul v-else role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="report in reports" :key="report.reportId"
          class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200 min-h-[44px] flex flex-row items-center justify-between gap-3">
          <div class="flex flex-col min-w-0 flex-1">
            <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 line-clamp-2 sm:truncate">
              {{ $t('report.types.' + report.targetType.toLowerCase()) }} {{ $t('user.reportList.targetType') }} - {{
                report.reasonType || report.contents || '-' }}
            </p>
            <p class="mt-0.5 sm:mt-1 text-xs text-gray-500 dark:text-gray-400">
              {{ formatDate(report.createdAt) }}
            </p>
          </div>
          <div class="flex items-center flex-shrink-0 self-center">
            <span class="px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full whitespace-nowrap" :class="{
              'bg-yellow-100 dark:bg-yellow-900/50 text-yellow-800 dark:text-yellow-400': report.status === 'PENDING',
              'bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400': report.status === 'RESOLVED',
              'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-400': report.status === 'REJECTED'
            }">
              {{ report.status === 'PENDING' ? $t('user.reportList.pending') : (report.status === 'RESOLVED' ?
                $t('user.reportList.processed') : $t('user.reportList.rejected')) }}
            </span>
          </div>
        </li>
      </ul>

      <div v-if="reports.length > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>
