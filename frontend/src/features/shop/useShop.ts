import { computed, type Ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { shopApi, type ShopItemPageParams, type ShopPageParams } from '@/api/shop'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import {
  AUTH_SCOPED_QUERY_META,
  isSessionGenerationCurrent,
  sessionQueryKey,
} from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'

export { shopQueryKeys } from '@/features/shop/shopQueryKeys'

export function useShopItems(params: Ref<ShopItemPageParams>) {
  return useApiPageQuery({
    queryKey: computed(() => shopQueryKeys.items(params.value)),
    request: (context) => shopApi.getItems(params.value, withQuerySignal(undefined, context)),
  })
}

export function useMyPurchases(params: Ref<ShopPageParams>) {
  return useApiPageQuery({
    queryKey: computed(() => shopQueryKeys.purchases(params.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (context) => shopApi.getMyPurchases(params.value, withQuerySignal(undefined, context)),
  })
}

export function usePurchaseShopItem() {
  const queryClient = useQueryClient()
  const authStore = useAuthStore()

  return useMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: (request: number | { itemId: number, signal?: AbortSignal }) => {
      const itemId = typeof request === 'number' ? request : request.itemId
      const signal = typeof request === 'number' ? undefined : request.signal
      return shopApi.purchaseItem(itemId, { skipGlobalErrorHandler: true, signal })
    },
    onSuccess: async (_data, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      const authKey = (queryKey: readonly unknown[]) => sessionQueryKey(context.sessionGeneration, queryKey)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: shopQueryKeys.itemsRoot }),
        queryClient.invalidateQueries({
          queryKey: authKey(shopQueryKeys.purchasesRoot),
        }),
        queryClient.invalidateQueries({
          queryKey: authKey(userQueryKeys.pointsRoot),
        }),
        queryClient.invalidateQueries({ queryKey: ['emoticon'] }),
        queryClient.invalidateQueries({ queryKey: ['emoticons'] }),
        queryClient.invalidateQueries({
          queryKey: authKey(['emoticon']),
        }),
        queryClient.invalidateQueries({
          queryKey: authKey(['emoticons', 'accessible', 'picker']),
        }),
      ])
    },
  })
}
