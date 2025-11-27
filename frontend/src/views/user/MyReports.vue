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
    <h1 class="text-2xl font-bold text-gray-900 mb-6">신고 목록</h1>

    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="report in reports" :key="report.id" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ report.targetType }} 신고 - {{ report.reason }}
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
                {{ report.status === 'PENDING' ? '대기중' : (report.status === 'PROCESSED' ? '처리됨' : '반려됨') }}
              </span>
            </div>
          </div>
        </li>
        <li v-if="reports.length === 0 && !loading" class="px-4 py-8 text-center text-gray-500">
          신고 내역이 없습니다.
        </li>
      </ul>
    </div>
  </div>
</template>
