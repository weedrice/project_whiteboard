<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Coins, ShoppingBag } from 'lucide-vue-next'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import { useConfirm } from '@/composables/useConfirm'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { useUser } from '@/composables/useUser'
import { usePurchaseShopItem, useShopItems } from '@/features/shop/useShop'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { formatInteger } from '@/utils/numberFormat'
import type { ShopItem } from '@/api/shop'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const { useMyPoint } = useUser()

const {
  page,
  handlePageChange,
  items,
  totalPages,
  isLoading,
  errorMessage,
  refetch,
} = usePaginatedListState<ShopItem>(useShopItems, { initialSize: 12, t })

const pointQueryEnabled = computed(() => authStore.isAuthenticated)
const pointQueryIdentity = computed(() => authStore.user?.userId ?? authStore.user?.loginId ?? null)
const { data: pointData } = useMyPoint(pointQueryEnabled, pointQueryIdentity)
const currentPoints = computed(() => pointData.value?.currentPoint ?? authStore.user?.points ?? 0)

const purchaseMutation = usePurchaseShopItem()
const purchasingItemId = ref<number | null>(null)
let purchaseRevision = 0
let purchaseController: AbortController | null = null

watch(() => authStore.sessionGeneration, () => {
  purchaseRevision += 1
  purchaseController?.abort()
  purchaseController = null
  purchasingItemId.value = null
})

const purchase = async (item: ShopItem) => {
  if (purchasingItemId.value !== null) return

  if (!authStore.isAuthenticated) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  if (currentPoints.value < item.price) {
    toastStore.addToast(t('shop.insufficientPoints', {
      current: formatInteger(currentPoints.value),
      price: formatInteger(item.price),
    }), 'error')
    return
  }

  const intent = captureAuthSessionIntent(authStore)
  const accepted = await confirm(t('shop.purchaseConfirm', {
    name: item.itemName,
    price: formatInteger(item.price),
  }))
  if (!accepted || !isAuthSessionIntentCurrent(authStore, intent)) return

  const revision = purchaseRevision
  const controller = new AbortController()
  purchaseController = controller
  purchasingItemId.value = item.itemId
  try {
    await purchaseMutation.mutateAsync({ itemId: item.itemId, signal: controller.signal })
    if (revision !== purchaseRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('shop.purchaseSuccess'), 'success')
  } catch (error) {
    if (revision !== purchaseRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(extractErrorMessage(error) || t('shop.purchaseFailed'), 'error')
  } finally {
    if (purchaseController === controller && revision === purchaseRevision) {
      purchaseController = null
      purchasingItemId.value = null
    }
  }
}
</script>

<template>
  <main class="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 sm:py-8 lg:px-8">
    <header class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <div class="mb-2 flex items-center gap-2">
          <ShoppingBag class="h-6 w-6 nv-accent-text" aria-hidden="true" />
          <h1 class="text-2xl font-bold nv-title">{{ t('shop.title') }}</h1>
        </div>
        <p class="text-sm nv-text-muted">{{ t('shop.description') }}</p>
      </div>
      <BaseCard padding="sm" elevation="none" bordered class="shrink-0">
        <div v-if="authStore.isAuthenticated" class="flex items-center gap-2 text-sm">
          <Coins class="h-4 w-4 nv-accent-text" aria-hidden="true" />
          <span class="nv-text-muted">{{ t('shop.currentPoints') }}</span>
          <strong class="nv-title">{{ formatInteger(currentPoints) }} P</strong>
        </div>
        <p v-else class="text-sm nv-text-muted">{{ t('shop.loginHint') }}</p>
      </BaseCard>
    </header>

    <div v-if="isLoading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true">
      <BaseCard v-for="index in 6" :key="index" bordered>
        <BaseSkeleton class="mb-3 h-5 w-2/3" />
        <BaseSkeleton class="mb-2 h-4 w-full" />
        <BaseSkeleton class="mb-6 h-4 w-4/5" />
        <BaseSkeleton class="h-9 w-full" />
      </BaseCard>
    </div>

    <ErrorState
      v-else-if="errorMessage"
      :message="errorMessage"
      show-retry
      @retry="refetch"
    />

    <EmptyState v-else-if="items.length === 0" :title="t('shop.empty')" />

    <template v-else>
      <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" role="list">
        <li v-for="item in items" :key="item.itemId">
          <BaseCard as="article" bordered class="h-full">
            <div class="flex h-full flex-col">
              <div class="mb-3 flex items-start justify-between gap-3">
                <h2 class="font-semibold nv-title">{{ item.itemName }}</h2>
                <BaseBadge variant="gray" size="sm">{{ item.itemType }}</BaseBadge>
              </div>
              <p class="mb-5 min-h-10 flex-1 text-sm nv-text-muted">
                {{ item.description || '-' }}
              </p>
              <div class="mb-3 flex items-center justify-between text-sm">
                <span class="nv-text-subtle">{{ t('shop.price') }}</span>
                <strong class="nv-title">{{ formatInteger(item.price) }} P</strong>
              </div>
              <BaseButton
                full-width
                :loading="purchasingItemId === item.itemId"
                :disabled="purchasingItemId !== null"
                @click="purchase(item)"
              >
                {{ authStore.isAuthenticated ? t('shop.purchase') : t('shop.loginToPurchase') }}
              </BaseButton>
            </div>
          </BaseCard>
        </li>
      </ul>

      <div class="mt-8">
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>
    </template>
  </main>
</template>
