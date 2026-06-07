<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUser } from '@/composables/useUser'
import { formatDate } from '@/utils/date'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import { Coins } from 'lucide-vue-next'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import type { PointHistory } from '@/types'

const { t } = useI18n()
const { useMyPointHistories } = useUser()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: history,
  totalPages,
  isLoading: loading,
  errorMessage,
  refetch,
} = usePaginatedListState<PointHistory>(useMyPointHistories, { initialSize: 15, t })
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.points')"
    :icon="Coins"
    :items-count="history.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.pointsHistory.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-2xl"
    header-class="px-3 py-3 sm:py-5 sm:px-6 gap-2"
    loading-preset="compact-status-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <ul role="list" class="divide-y divide-[var(--nv-border)]">
      <li v-for="item in history" :key="item.historyId"
        class="px-3 py-2.5 sm:px-6 sm:py-4 nv-hover-surface transition-colors duration-200 flex items-center">
        <div class="flex flex-row items-center justify-between w-full gap-2 min-w-0">
          <div class="flex flex-col min-w-0 flex-1">
            <p class="text-xs sm:text-sm font-medium nv-accent-text truncate">
              {{ item.description || $t('user.pointsHistory.adjustment') }}
            </p>
            <p class="mt-0.5 text-[11px] sm:text-xs nv-text-subtle">
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
