<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'
import Pagination from '@/components/common/Pagination.vue'

const history = ref([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const size = ref(20)

const fetchHistory = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/points/me/history', {
      params: {
        page: page.value,
        size: size.value
      }
    })
    if (data.success) {
      history.value = data.data.content
      totalPages.value = data.data.totalPages
    }
  } catch (error) {
    console.error('포인트 내역을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchHistory()
}

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString()
}

onMounted(() => {
  fetchHistory()
})
</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="item in history" :key="item.id" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ item.description || $t('user.pointsHistory.adjustment') }}
              </p>
              <p class="mt-1 text-xs text-gray-500">
                {{ formatDate(item.createdAt) }}
              </p>
            </div>
            <div class="flex items-center">
              <span 
                class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                :class="item.amount > 0 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
              >
                {{ item.amount > 0 ? '+' : '' }}{{ item.amount }} P
              </span>
            </div>
          </div>
        </li>
        <li v-if="history.length === 0 && !loading" class="px-4 py-8 text-center text-gray-500">
          {{ $t('user.pointsHistory.empty') }}
        </li>
      </ul>
      
      <div v-if="history.length > 0" class="bg-gray-50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination 
          :current-page="page" 
          :total-pages="totalPages"
          @page-change="handlePageChange" 
        />
      </div>
    </div>
  </div>
</template>
