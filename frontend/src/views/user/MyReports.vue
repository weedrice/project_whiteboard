<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'

const reports = ref([])
const loading = ref(false)

const fetchReports = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/reports/me')
    if (data.success) {
      reports.value = data.data.content
    }
  } catch (error) {
    console.error('신고 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
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
    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="report in reports" :key="report.id" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ report.targetType }} {{ $t('user.reportList.targetType') }} - {{ report.reason }}
              </p>
              <p class="mt-1 text-xs text-gray-500">
                {{ formatDate(report.createdAt) }}
              </p>
            </div>
            <div class="flex items-center">
              <span 
                class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                :class="{
                  'bg-yellow-100 text-yellow-800': report.status === 'PENDING',
                  'bg-green-100 text-green-800': report.status === 'PROCESSED',
                  'bg-red-100 text-red-800': report.status === 'REJECTED'
                }"
              >
                {{ report.status === 'PENDING' ? $t('user.reportList.pending') : (report.status === 'PROCESSED' ? $t('user.reportList.processed') : $t('user.reportList.rejected')) }}
              </span>
            </div>
          </div>
        </li>
        <li v-if="reports.length === 0 && !loading" class="px-4 py-8 text-center text-gray-500">
          {{ $t('user.reportList.empty') }}
        </li>
      </ul>
    </div>
  </div>
</template>
