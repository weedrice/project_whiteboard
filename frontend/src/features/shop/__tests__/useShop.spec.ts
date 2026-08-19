import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'

const invalidateQueries = vi.hoisted(() => vi.fn(async () => undefined))
const mutationOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)
const pageQueryOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)
const authState = vi.hoisted(() => ({ sessionGeneration: 0 }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

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
    authState.sessionGeneration = 0
  })

  it('keys item and purchase pages by valid pagination values', () => {
    const itemParams = ref({ page: 0, size: 12 })
    const purchaseParams = ref({ page: 2, size: 15 })

    useShopItems(itemParams)
    useMyPurchases(purchaseParams)

    expect((pageQueryOptions[0]?.queryKey as { value: unknown[] }).value).toEqual(['shop', 'items', 0, 12, ''])
    expect((pageQueryOptions[1]?.queryKey as { value: unknown[] }).value).toEqual(['shop', 'purchases', 2, 15])
    expect(pageQueryOptions[0]).toMatchObject({
      refetchInterval: 30_000,
      refetchOnWindowFocus: 'always',
      refetchOnReconnect: 'always',
    })
  })

  it('keeps refs out of factories and updates computed consumer keys', () => {
    const params = ref({ page: 0, size: 12, itemType: 'EMOTICON' as const })
    const key = computed(() => shopQueryKeys.items(params.value))

    expect(shopQueryKeys.items(params.value)).not.toContain(params)
    expect(key.value).toEqual(['shop', 'items', 0, 12, 'EMOTICON'])

    params.value = { page: 3, size: 24, itemType: 'EMOTICON' }

    expect(key.value).toEqual(['shop', 'items', 3, 24, 'EMOTICON'])
  })

  it('suppresses the global purchase error and invalidates points, catalog, history, and emoticons', async () => {
    usePurchaseShopItem()
    const options = mutationOptions[0]

    expect(options?.meta).toEqual({ errorMessage: false })

    await (options?.mutationFn as (itemId: number) => Promise<unknown>)(9)
    expect(shopApi.purchaseItem).toHaveBeenCalledWith(9, { skipGlobalErrorHandler: true, signal: undefined })

    const context = (options?.onMutate as () => { sessionGeneration: number })()
    await (options?.onSuccess as (_data: unknown, _variables: unknown, context: unknown) => Promise<void>)(
      undefined,
      9,
      context,
    )
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: shopQueryKeys.itemsRoot })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, ...shopQueryKeys.purchasesRoot],
    })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', 'points'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticons'] })
  })

  it('skips cache invalidation when the session changes before purchase completes', async () => {
    usePurchaseShopItem()
    const options = mutationOptions[0]
    const context = (options?.onMutate as () => { sessionGeneration: number })()
    authState.sessionGeneration = 1

    await (options?.onSuccess as (_data: unknown, _variables: unknown, context: unknown) => Promise<void>)(
      undefined,
      9,
      context,
    )

    expect(invalidateQueries).not.toHaveBeenCalled()
  })
})
