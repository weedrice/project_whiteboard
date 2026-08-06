<script setup lang="ts">
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUser } from '@/features/user/useUser'
import { formatDate } from '@/utils/date'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { Coins } from 'lucide-vue-next'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import type { PointHistory, PointHistoryType } from '@/types'
import type { PointHistoryParams } from '@/api/user'

const { t } = useI18n()
const { useMyPointHistories } = useUser()
const typeFilter = ref<PointHistoryType | ''>('')
const useFilteredPointHistories = (params: Ref<{ page: number; size: number }>) => {
  const filteredParams = computed<PointHistoryParams>(() => ({
    ...params.value,
    ...(typeFilter.value ? { type: typeFilter.value } : {}),
  }))
  return useMyPointHistories(filteredParams)
}
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
} = usePaginatedListState<PointHistory>(useFilteredPointHistories, { initialSize: 15, t })

function handleTypeFilterChange(value: string | number) {
  typeFilter.value = typeof value === 'string' ? value as PointHistoryType | '' : ''
  page.value = 0
}

const transactionKind = (item: PointHistory) => {
  if (item.type === 'EXPIRE') return 'expired'
  if (item.amount > 0 || item.type === 'EARN') return 'earned'
  return 'spent'
}
const transactionLabel = (item: PointHistory) => t(`user.pointsHistory.transaction.${transactionKind(item)}`)
const transactionClass = (item: PointHistory) => {
  const kind = transactionKind(item)
  if (kind === 'earned') {
    return 'border-[var(--nv-success-border)] bg-[var(--nv-success-bg)] text-[var(--nv-success-text)]'
  }
  if (kind === 'expired') {
    return 'border-[var(--nv-warning-border)] bg-[var(--nv-warning-bg)] text-[var(--nv-warning-text)]'
  }
  return 'border-[var(--nv-danger-border)] bg-[var(--nv-danger-bg)] text-[var(--nv-danger-text)]'
}
const transactionBadgeVariant = (item: PointHistory): 'success' | 'warning' | 'danger' => {
  const kind = transactionKind(item)
  if (kind === 'earned') return 'success'
  if (kind === 'expired') return 'warning'
  return 'danger'
}

const formatSignedPoint = (amount: number) => `${amount > 0 ? '+' : ''}${amount.toLocaleString()} P`
const formatBalance = (balance: number) => `${balance.toLocaleString()} P`
</script>

<template>
  <PaginatedListCard
    title-tag="h1"
    :title="$t('user.tabs.points')"
    :icon="Coins"
    :items-count="history.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.pointsHistory.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    header-class="px-3 py-3 sm:py-5 sm:px-6 gap-2"
    loading-preset="compact-status-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #subheader>
      <BaseSelect
        id="point-history-type-filter"
        :model-value="typeFilter"
        :label="$t('user.pointsHistory.typeFilter')"
        :options="[
          { value: '', label: $t('user.pointsHistory.types.ALL') },
          { value: 'EARN', label: $t('user.pointsHistory.types.EARN') },
          { value: 'SPEND', label: $t('user.pointsHistory.types.SPEND') },
          { value: 'EXPIRE', label: $t('user.pointsHistory.types.EXPIRE') },
          { value: 'PENALTY', label: $t('user.pointsHistory.types.PENALTY') },
          { value: 'REWARD_REVERSAL', label: $t('user.pointsHistory.types.REWARD_REVERSAL') },
        ]"
        input-class="sm:w-52"
        @update:model-value="handleTypeFilterChange"
      />
    </template>
    <ul role="list" class="divide-y divide-[var(--nv-line)]">
      <li v-for="item in history" :key="item.historyId"
        class="px-3 py-3 sm:px-6 sm:py-4 nv-hover-surface transition-colors duration-200">
        <div class="flex items-start justify-between w-full gap-3 min-w-0">
          <div class="flex min-w-0 flex-1 items-start gap-3">
            <span
              class="mt-0.5 inline-flex h-6 min-w-10 shrink-0 items-center justify-center rounded-full border px-2 text-xs font-semibold"
              :class="transactionClass(item)"
            >
              {{ transactionLabel(item) }}
            </span>
            <div class="flex flex-col min-w-0 flex-1">
              <p class="text-xs sm:text-sm font-medium nv-text truncate">
                {{ item.description || $t('user.pointsHistory.adjustment') }}
              </p>
              <p class="mt-1 text-xs nv-text-subtle">
                {{ formatDate(item.createdAt) }}
              </p>
            </div>
          </div>
          <div class="flex shrink-0 flex-col items-end gap-1">
            <BaseBadge :variant="transactionBadgeVariant(item)" size="sm"
              class="text-xs px-2 py-0.5 font-semibold">
              {{ formatSignedPoint(item.amount) }}
            </BaseBadge>
            <span class="text-xs nv-text-subtle">
              {{ formatBalance(item.balanceAfter) }}
            </span>
          </div>
        </div>
      </li>
    </ul>
  </PaginatedListCard>
</template>
