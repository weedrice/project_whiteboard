<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ShoppingBag } from 'lucide-vue-next'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { useMyPurchases } from '@/features/shop/useShop'
import { formatDate } from '@/utils/date'
import { formatInteger } from '@/utils/numberFormat'
import type { PurchaseHistory } from '@/api/shop'

const { t } = useI18n()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: purchases,
  totalPages,
  isLoading,
  errorMessage,
  refetch,
} = usePaginatedListState<PurchaseHistory>(useMyPurchases, { initialSize: 15, t })
</script>

<template>
  <PaginatedListCard
    title-tag="h1"
    :title="t('shop.purchases.title')"
    :icon="ShoppingBag"
    :items-count="purchases.length"
    :loading="isLoading"
    :error="errorMessage || null"
    :empty-title="t('shop.purchases.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-3xl"
    loading-preset="avatar-action-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <ul role="list" class="divide-y divide-[var(--nv-line)]">
      <li
        v-for="purchase in purchases"
        :key="purchase.purchaseId"
        class="flex items-center gap-3 px-4 py-4 sm:px-6"
      >
        <img
          v-if="purchase.item.imageUrl"
          :src="purchase.item.imageUrl"
          :alt="t('shop.purchases.imageAlt', { name: purchase.item.itemName })"
          class="h-12 w-12 shrink-0 rounded-lg object-cover nv-surface-muted"
        />
        <div v-else class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg nv-surface-muted">
          <ShoppingBag class="h-5 w-5 nv-text-subtle" aria-hidden="true" />
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold nv-title">{{ purchase.item.itemName }}</p>
          <p class="mt-1 text-xs nv-text-subtle">
            {{ t('shop.purchases.purchasedAt') }} · {{ formatDate(purchase.purchasedAt) }}
          </p>
        </div>
        <div class="shrink-0 text-right">
          <p class="text-xs nv-text-subtle">{{ t('shop.purchases.purchasedPrice') }}</p>
          <p class="mt-1 text-sm font-semibold nv-title">{{ formatInteger(purchase.price) }} P</p>
        </div>
      </li>
    </ul>
  </PaginatedListCard>
</template>
