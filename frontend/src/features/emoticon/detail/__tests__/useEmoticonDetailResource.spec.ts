import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref, type ComputedRef } from 'vue'
import { useEmoticonDetailResource } from '../useEmoticonDetailResource'
import { emoticonApiData, emoticonApiSuccess } from '@/test/emoticonApiFixtures'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
  apiQueryOptions: [] as Array<Record<string, unknown>>,
  invalidateQueries: vi.fn(),
  addToast: vi.fn(),
  getEmoticon: vi.fn(),
  checkPurchaseStatus: vi.fn(),
  purchaseEmoticon: vi.fn(),
  isAuthenticated: true,
  user: { userId: 7 },
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
  useMutation: (options: Record<string, unknown>) => ({
    mutate: async (variables: unknown) => {
      const context = await (options.onMutate as ((variables: unknown) => unknown) | undefined)?.(variables)
      const result = await (options.mutationFn as (variables: unknown) => Promise<unknown>)(variables)
      await (options.onSuccess as ((result: unknown, variables: unknown, context: unknown) => void) | undefined)?.(
        result,
        variables,
        context,
      )
      return result
    },
    isPending: ref(false),
  }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: (options: Record<string, unknown>) => {
    mocks.apiQueryOptions.push(options)
    return {
      data: ref(undefined),
      isLoading: ref(false),
      isFetching: ref(false),
      error: ref(null),
    }
  },
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getEmoticon: mocks.getEmoticon,
    checkPurchaseStatus: mocks.checkPurchaseStatus,
    purchaseEmoticon: mocks.purchaseEmoticon,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: mocks.isAuthenticated,
    user: mocks.user,
    sessionGeneration: 0,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('useEmoticonDetailResource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.apiQueryOptions.length = 0
    mocks.isAuthenticated = true
    mocks.user = { userId: 7 }
  })

  it('uses API query helpers for detail and purchase status', async () => {
    const emoticonId = computed(() => 11)

    useEmoticonDetailResource(emoticonId)

    const detailOptions = mocks.apiQueryOptions[0]
    const purchaseOptions = mocks.apiQueryOptions[1]

    expect((detailOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticon', 11])
    expect((purchaseOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticon', 11, 'purchased'])
    expect((detailOptions.enabled as ComputedRef<boolean>).value).toBe(true)
    expect((purchaseOptions.enabled as ComputedRef<boolean>).value).toBe(true)

    mocks.getEmoticon.mockResolvedValueOnce(emoticonApiData({ emoticonId: 11 }))
    mocks.checkPurchaseStatus.mockResolvedValueOnce(emoticonApiData({ purchased: false, available: true, price: 100 }))

    await (detailOptions.request as () => Promise<unknown>)()
    await (purchaseOptions.request as () => Promise<unknown>)()

    expect(mocks.getEmoticon).toHaveBeenCalledWith(11)
    expect(mocks.checkPurchaseStatus).toHaveBeenCalledWith(11)
  })

  it('loads public purchase availability for anonymous visitors', () => {
    mocks.isAuthenticated = false
    const emoticonId = computed(() => 13)

    useEmoticonDetailResource(emoticonId)

    const purchaseOptions = mocks.apiQueryOptions[1]
    expect((purchaseOptions.enabled as ComputedRef<boolean>).value).toBe(true)
  })

  it('keeps purchase cache invalidation behavior', async () => {
    const emoticonId = computed(() => 12)
    mocks.purchaseEmoticon.mockResolvedValueOnce(emoticonApiSuccess())

    const resource = useEmoticonDetailResource(emoticonId)
    await resource.purchase()

    expect(mocks.purchaseEmoticon).toHaveBeenCalledWith(12, { skipGlobalErrorHandler: true })
    expect(mocks.addToast).toHaveBeenCalledWith('emoticon.purchase.success', 'success')
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', 12] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'emoticon', 12, 'purchased'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'emoticons', 'accessible', 'picker'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticons'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', 'points'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(5)
  })

  it('invalidates the purchased emoticon when the route changes before success', async () => {
    const currentId = ref(12)
    const emoticonId = computed(() => currentId.value)
    const purchaseResponse = emoticonApiSuccess()
    const purchase = createDeferred<typeof purchaseResponse>()
    mocks.purchaseEmoticon.mockReturnValueOnce(purchase.promise)

    const resource = useEmoticonDetailResource(emoticonId)
    const pendingPurchase = resource.purchase()
    currentId.value = 13
    purchase.resolve(purchaseResponse)
    await pendingPurchase

    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', 12] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'emoticon', 12, 'purchased'] })
    expect(mocks.invalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['emoticon', 13] })
  })
})
