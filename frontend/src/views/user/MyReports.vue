<script setup>
import { ref, onMounted } from 'vue'
import { reportApi } from '@/api/report'
import Pagination from '@/components/common/Pagination.vue'
import PageSizeSelector from '@/components/common/PageSizeSelector.vue'

const reports = ref([])
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
  } catch (error) {
    console.error('신고 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchReports()
}

const handleSizeChange = () => {
  page.value = 0
  fetchReports()
}

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString()
}

onMounted(() => {
  fetchReports()
})
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200 dark:border-gray-700">
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.tabs.reports') }}</h3>
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
      </div>
      <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="report in reports" :key="report.id" class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                {{ report.targetType }} {{ $t('user.reportList.targetType') }} - {{ report.reason }}
              </p>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ formatDate(report.createdAt) }}
              </p>
            </div>
            <div class="flex items-center">
              <span 
                class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                :class="{
                  'bg-yellow-100 dark:bg-yellow-900/50 text-yellow-800 dark:text-yellow-400': report.status === 'PENDING',
                  'bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400': report.status === 'PROCESSED',
                  'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-400': report.status === 'REJECTED'
                }"
              >
                {{ report.status === 'PENDING' ? $t('user.reportList.pending') : (report.status === 'PROCESSED' ? $t('user.reportList.processed') : $t('user.reportList.rejected')) }}
              </span>
            </div>
          </div>
        </li>
        <li v-if="reports.length === 0 && !loading" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
          {{ $t('user.reportList.empty') }}
        </li>
      </ul>
      
      <div v-if="reports.length > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination 
          :current-page="page" 
          :total-pages="totalPages"
          @page-change="handlePageChange" 
        />
      </div>
    </div>
  </div>
</template>
