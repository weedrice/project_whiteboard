import { computed, type Ref } from 'vue'
import { shopApi, type ShopPageParams } from '@/api/shop'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'

export { shopQueryKeys } from '@/features/shop/shopQueryKeys'

export function useMyPurchases(params: Ref<ShopPageParams>) {
  return useApiPageQuery({
    queryKey: computed(() => shopQueryKeys.purchases(params.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (context) => shopApi.getMyPurchases(params.value, withQuerySignal(undefined, context)),
  })
}
