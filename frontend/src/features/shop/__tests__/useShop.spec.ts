import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const invalidateQueries = vi.hoisted(() => vi.fn(async () => undefined))
const mutationOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)
const pageQueryOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useMutation: (options: Record<string, unknown>) => {
    mutationOptions.push(options)
    return { mutate: vi.fn(), mutateAsync: vi.fn(), isPending: ref(false) }
  },
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiPageQuery: (options: Record<string, unknown>) => {
    pageQueryOptions.push(options)
    return { data: ref(null), isLoading: ref(false), isError: ref(false), error: ref(null) }
  },
}))

vi.mock('@/api/shop', () => ({
  shopApi: {
    getItems: vi.fn(),
    getMyPurchases: vi.fn(),
    purchaseItem: vi.fn(),
  },
}))

import { shopApi } from '@/api/shop'
import { shopQueryKeys, useMyPurchases, usePurchaseShopItem, useShopItems } from '../useShop'

describe('shop resources', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutationOptions.length = 0
    pageQueryOptions.length = 0
  })

  it('keys item and purchase pages by valid pagination values', () => {
    const itemParams = ref({ page: 0, size: 12 })
    const purchaseParams = ref({ page: 2, size: 15 })

    useShopItems(itemParams)
    useMyPurchases(purchaseParams)

    expect((pageQueryOptions[0]?.queryKey as { value: unknown[] }).value).toEqual(['shop', 'items', 0, 12, ''])
    expect((pageQueryOptions[1]?.queryKey as { value: unknown[] }).value).toEqual(['shop', 'purchases', 2, 15])
  })

  it('suppresses the global purchase error and invalidates points, catalog, history, and emoticons', async () => {
    usePurchaseShopItem()
    const options = mutationOptions[0]

    await (options?.mutationFn as (itemId: number) => Promise<unknown>)(9)
    expect(shopApi.purchaseItem).toHaveBeenCalledWith(9, { skipGlobalErrorHandler: true })

    await (options?.onSuccess as () => Promise<void>)()
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: shopQueryKeys.itemsRoot })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: shopQueryKeys.purchasesRoot })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'points'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticons'] })
  })
})
