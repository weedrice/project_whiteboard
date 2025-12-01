<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'

const history = ref([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)

const fetchHistory = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/points/me/history', {
      params: {
        page: page.value,
        size: 20
      }
    })
    if (data.success) {
      if (page.value === 0) {
        history.value = data.data.content
      } else {
        history.value.push(...data.data.content)
      }
      // Ensure hasMore is false if no content or empty content
      hasMore.value = !data.data.last && data.data.content.length > 0
    }
  } catch (error) {
    console.error('포인트 내역을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value++
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
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">포인트 내역</h1>

    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="item in history" :key="item.id" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ item.reason || '포인트 조정' }}
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
          포인트 내역이 없습니다.
        </li>
      </ul>
      <div v-if="hasMore && history.length > 0" class="bg-gray-50 px-4 py-4 sm:px-6 text-center">
        <button 
          @click="loadMore" 
          :disabled="loading"
          class="text-sm font-medium text-indigo-600 hover:text-indigo-500 disabled:opacity-50"
        >
          {{ loading ? '로딩 중...' : '더 보기' }}
        </button>
      </div>
    </div>
  </div>
</template>
