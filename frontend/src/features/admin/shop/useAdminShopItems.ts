import { computed, type Ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { adminApi, type AdminShopItemSaleStatusData } from '@/api/admin'
import type { AdminShopItem, AdminShopItemSearchParams } from '@/types'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
    callAdminApiWithOptionalConfig,
    useAdminPageQuery,
} from '@/features/admin/queries/adminApiQuery'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'
import { useAuthStore } from '@/stores/auth'
import {
    captureSessionGeneration,
    isSessionGenerationCurrent,
    sessionQueryKey,
} from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'

interface UpdateSaleStatusRequest extends AdminShopItemSaleStatusData {
    itemId: number
}

export function useAdminShopItems(params: Ref<AdminShopItemSearchParams>) {
    return useAdminPageQuery<AdminShopItem>(
        computed(() => adminQueryKeys.shopItems(params.value)),
        (config) => callAdminApiWithOptionalConfig(
            config,
            (requestConfig) => adminApi.getShopItems(params.value, requestConfig),
            () => adminApi.getShopItems(params.value),
        ),
    )
}

export function useUpdateAdminShopItemSaleStatus() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()

    return useMutation({
        meta: LOCAL_MUTATION_ERROR_META,
        mutationFn: ({ itemId, ...data }: UpdateSaleStatusRequest) =>
            adminApi.updateShopItemSaleStatus(itemId, data),
        onMutate: () => ({ sessionGeneration: captureSessionGeneration(authStore) }),
        onSuccess: async (_response, _variables, context) => {
            if (!isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(
                        context.sessionGeneration,
                        adminQueryKeys.shopItemsRoot,
                    ),
                }),
                queryClient.invalidateQueries({ queryKey: shopQueryKeys.itemsRoot }),
                queryClient.invalidateQueries({ queryKey: ['emoticon'] }),
                queryClient.invalidateQueries({ queryKey: ['emoticons'] }),
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(
                        context.sessionGeneration,
                        ['emoticon'],
                    ),
                }),
            ])
        },
    })
}
