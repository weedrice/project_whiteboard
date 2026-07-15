import { computed, type Ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { shopApi, type ShopItemPageParams, type ShopPageParams } from '@/api/shop'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'

export const shopQueryKeys = {
  root: ['shop'] as const,
  itemsRoot: ['shop', 'items'] as const,
  items: (params: Ref<ShopItemPageParams>) => computed(() => [
    ...shopQueryKeys.itemsRoot,
    params.value.page ?? 0,
    params.value.size ?? 20,
    params.value.itemType ?? '',
  ] as const),
  purchasesRoot: ['shop', 'purchases'] as const,
  purchases: (params: Ref<ShopPageParams>) => computed(() => [
    ...shopQueryKeys.purchasesRoot,
    params.value.page ?? 0,
    params.value.size ?? 20,
  ] as const),
}

export function useShopItems(params: Ref<ShopItemPageParams>) {
  return useApiPageQuery({
    queryKey: shopQueryKeys.items(params),
    request: (context) => shopApi.getItems(params.value, withQuerySignal(undefined, context)),
  })
}

export function useMyPurchases(params: Ref<ShopPageParams>) {
  return useApiPageQuery({
    queryKey: shopQueryKeys.purchases(params),
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
