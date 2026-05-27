<script setup lang="ts">
import { onMounted } from 'vue'
import { userApi } from '@/api/user'
import type { PointHistory } from '@/types'
import { formatDate } from '@/utils/date'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { Coins } from 'lucide-vue-next'
import { usePagination } from '@/composables/usePagination'

const {
  items: history,
  loading,
  page,
  size,
  totalPages,
  error,
  fetch: fetchHistory,
  handlePageChange,
  handleSizeChange
} = usePagination<PointHistory>(async (params, { signal }) => {
  const { data } = await userApi.getMyPointHistories(params, { signal })
  return data
}, { page: 0, size: 15 })

onMounted(() => {
  fetchHistory()
})
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.points')"
    :icon="Coins"
    :items-count="history.length"
    :loading="loading"
    :error="error"
    :empty-title="$t('user.pointsHistory.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-2xl"
    header-class="px-3 py-3 sm:py-5 sm:px-6 gap-2"
    @retry="fetchHistory"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-3 py-2.5 sm:px-6 sm:py-4 flex justify-between items-center">
          <div class="flex flex-col flex-1 min-w-0">
            <BaseSkeleton width="60%" height="14px" className="mb-1" />
            <BaseSkeleton width="40%" height="12px" />
          </div>
          <BaseSkeleton width="48px" height="20px" rounded="rounded-full" />
        </div>
      </div>
    </template>

    <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
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
  </PaginatedListCard>
</template>
