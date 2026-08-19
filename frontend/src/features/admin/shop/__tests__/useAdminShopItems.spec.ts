import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { shopQueryKeys } from '@/features/shop/shopQueryKeys'

const invalidateQueries = vi.hoisted(() => vi.fn(async () => undefined))
const mutationOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)
const authState = vi.hoisted(() => ({ sessionGeneration: 4 }))
type MutationContext = { sessionGeneration: number }

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useMutation: (options: Record<string, unknown>) => {
    mutationOptions.push(options)
    return { mutateAsync: vi.fn(), isPending: ref(false) }
  },
}))

vi.mock('@/features/admin/queries/adminApiQuery', () => ({
  callAdminApiWithOptionalConfig: vi.fn(),
  useAdminPageQuery: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  adminApi: {
    getShopItems: vi.fn(),
    updateShopItemSaleStatus: vi.fn(),
  },
}))

import { adminApi } from '@/api/admin'
import { useUpdateAdminShopItemSaleStatus } from '../useAdminShopItems'

describe('useUpdateAdminShopItemSaleStatus', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutationOptions.length = 0
    authState.sessionGeneration = 4
  })

  it('updates the status and invalidates public and auth-scoped purchase availability', async () => {
    useUpdateAdminShopItemSaleStatus()
    const options = mutationOptions[0]
    const request = { itemId: 17, saleEnabled: false, reason: 'review' }

    await (options?.mutationFn as (value: typeof request) => Promise<unknown>)(request)
    expect(adminApi.updateShopItemSaleStatus).toHaveBeenCalledWith(17, {
      saleEnabled: false,
      reason: 'review',
    })

    const context = (options?.onMutate as () => MutationContext)()
    await (options?.onSuccess as (
      data: unknown,
      variables: typeof request,
      context: MutationContext,
    ) => Promise<void>)(undefined, request, context)

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 4, ...adminQueryKeys.shopItemsRoot],
    })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: shopQueryKeys.itemsRoot })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticons'] })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 4, 'emoticon'],
    })
  })

  it('does not invalidate a newer session after the mutation completes', async () => {
    useUpdateAdminShopItemSaleStatus()
    const options = mutationOptions[0]
    const context = (options?.onMutate as () => MutationContext)()
    authState.sessionGeneration = 5

    await (options?.onSuccess as (
      data: unknown,
      variables: unknown,
      context: MutationContext,
    ) => Promise<void>)(undefined, undefined, context)

    expect(invalidateQueries).not.toHaveBeenCalled()
  })
})
