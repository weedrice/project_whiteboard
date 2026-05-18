<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import type { PointHistory } from '@/types'
import { formatDate } from '@/utils/date'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { Coins } from 'lucide-vue-next'
import logger from '@/utils/logger'

const history = ref<PointHistory[]>([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const size = ref(15)

const fetchHistory = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getMyPointHistories({
      page: page.value,
      size: size.value
    })
    if (data.success) {
      history.value = data.data.content
      totalPages.value = data.data.totalPages
    }
  } catch (error: unknown) {
    logger.error('Failed to load point history:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage: number) => {
  page.value = newPage
  fetchHistory()
}

const handleSizeChange = () => {
  page.value = 0
  fetchHistory()
}

onMounted(() => {
  fetchHistory()
})
</script>

<template>
  <div class="max-w-2xl mx-auto py-3 sm:py-6 md:py-8 px-3 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        class="px-3 py-3 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-2 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-base sm:text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <Coins class="h-4 w-4 sm:h-5 sm:w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ $t('user.tabs.points') }}
        </h3>
        <div class="hidden sm:block">
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
        </div>
      </div>
      <div v-if="loading && history.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-3 py-2.5 sm:px-6 sm:py-4 flex justify-between items-center">
          <div class="flex flex-col flex-1 min-w-0">
            <BaseSkeleton width="60%" height="14px" className="mb-1" />
            <BaseSkeleton width="40%" height="12px" />
          </div>
          <BaseSkeleton width="48px" height="20px" rounded="rounded-full" />
        </div>
      </div>
      <EmptyState v-else-if="history.length === 0" :title="$t('user.pointsHistory.empty')" :icon="Coins" />
      <ul v-else role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="item in history" :key="item.historyId"
          class="px-3 py-2.5 sm:px-6 sm:py-4 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200 flex items-center">
          <div class="flex flex-row items-center justify-between w-full gap-2 min-w-0">
            <div class="flex flex-col min-w-0 flex-1">
              <p class="text-xs sm:text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                {{ item.description || $t('user.pointsHistory.adjustment') }}
              </p>
              <p class="mt-0.5 text-[11px] sm:text-xs text-gray-500 dark:text-gray-400">
                {{ formatDate(item.createdAt) }}
              </p>
            </div>
            <div class="flex items-center flex-shrink-0">
              <BaseBadge :variant="item.amount > 0 ? 'success' : 'danger'" size="sm"
                class="text-[11px] sm:text-xs px-2 py-0.5">
                {{ item.amount > 0 ? '+' : '' }}{{ item.amount }} P
              </BaseBadge>
            </div>
          </div>
        </li>
      </ul>

      <div v-if="history.length > 0"
        class="bg-gray-50 dark:bg-gray-900/50 px-3 py-3 sm:px-6 sm:py-4 flex justify-center">
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>
