import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'

const pageQueryOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)

vi.mock('@/composables/useApiQuery', () => ({
  useApiPageQuery: (options: Record<string, unknown>) => {
    pageQueryOptions.push(options)
    return { data: ref(null), isLoading: ref(false), isError: ref(false), error: ref(null) }
  },
}))

vi.mock('@/api/shop', () => ({
  shopApi: { getMyPurchases: vi.fn() },
}))

import { shopApi } from '@/api/shop'
import { useMyPurchases } from '../useShop'

describe('shop purchase resources', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    pageQueryOptions.length = 0
  })

  it('keys purchase pages by pagination values and scopes them to the auth session', () => {
    useMyPurchases(ref({ page: 2, size: 15 }))

    expect((pageQueryOptions[0]?.queryKey as { value: unknown[] }).value).toEqual(['shop', 'purchases', 2, 15])
    expect(pageQueryOptions[0]?.meta).toEqual(AUTH_SCOPED_QUERY_META)
  })

  it('updates computed pagination keys and forwards the request cancellation signal', () => {
    const params = ref({ page: 0, size: 12 })
    useMyPurchases(params)
    const options = pageQueryOptions[0]
    const key = options?.queryKey as { value: unknown[] }
    expect(key.value).toEqual(['shop', 'purchases', 0, 12])

    params.value = { page: 3, size: 24 }
    expect(key.value).toEqual(['shop', 'purchases', 3, 24])

    const controller = new AbortController()
    const request = options?.request as (context: { signal: AbortSignal }) => unknown
    request({ signal: controller.signal })
    expect(shopApi.getMyPurchases).toHaveBeenCalledWith(params.value, { signal: controller.signal })
  })
})
