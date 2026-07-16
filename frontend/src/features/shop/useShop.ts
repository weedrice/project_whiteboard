import { computed, type Ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { shopApi, type ShopItemPageParams, type ShopPageParams } from '@/api/shop'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'

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
    request: (context) => shopApi.getMyPurchases(params.value, withQuerySignal(undefined, context)),
  })
}

export function usePurchaseShopItem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (itemId: number) => shopApi.purchaseItem(itemId, { skipGlobalErrorHandler: true }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: shopQueryKeys.itemsRoot }),
        queryClient.invalidateQueries({ queryKey: shopQueryKeys.purchasesRoot }),
        queryClient.invalidateQueries({ queryKey: userQueryKeys.pointsRoot }),
        queryClient.invalidateQueries({ queryKey: ['emoticon'] }),
        queryClient.invalidateQueries({ queryKey: ['emoticons'] }),
      ])
    },
  })
}
