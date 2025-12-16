<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import logger from '@/utils/logger'

const history = ref([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const size = ref(15)

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
    logger.error('Failed to load point history:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchHistory()
}

import { formatDate } from '@/utils/date'

const handleSizeChange = () => {
  page.value = 0
  fetchHistory()
}

onMounted(() => {
  fetchHistory()
})
</script>

<template>
  <div class="max-w-2xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.tabs.points') }}</h3>
        <PageSizeSelector v-model="size" @change="handleSizeChange" />
      </div>
      <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="item in history" :key="item.id"
          class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                {{ item.description || $t('user.pointsHistory.adjustment') }}
              </p>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ formatDate(item.createdAt) }}
              </p>
            </div>
            <div class="flex items-center">
              <BaseBadge :variant="item.amount > 0 ? 'success' : 'danger'" size="sm">
                {{ item.amount > 0 ? '+' : '' }}{{ item.amount }} P
              </BaseBadge>
            </div>
          </div>
        </li>
        <li v-if="history.length === 0 && !loading" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
          {{ $t('user.pointsHistory.empty') }}
        </li>
      </ul>

      <div v-if="history.length > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>

