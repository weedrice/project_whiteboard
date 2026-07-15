import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const routeParams = vi.hoisted(() => ({ emoticonId: '9' }))
const invalidateQueries = vi.hoisted(() => vi.fn())
const addToast = vi.hoisted(() => vi.fn())
const purchaseEmoticon = vi.hoisted(() => vi.fn())
const purchaseMutate = vi.hoisted(() => vi.fn())
const confirmPurchase = vi.hoisted(() => vi.fn(async () => true))
const mutationOptions = vi.hoisted(() => [] as Array<Record<string, unknown>>)
const purchaseStatusState = vi.hoisted(() => ({
  hasStatus: true,
  available: true,
  isLoading: false,
  isFetching: false,
  hasError: false,
  purchased: false,
  price: 100,
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useQuery: vi.fn((options: Record<string, unknown>) => {
    const key = options.queryKey as unknown[]
    if (Array.isArray(key) && key[2] === 'purchased') {
      return {
        data: ref(purchaseStatusState.hasStatus ? {
          purchased: purchaseStatusState.purchased,
          available: purchaseStatusState.available,
          price: purchaseStatusState.price,
        } : undefined),
        isLoading: ref(purchaseStatusState.isLoading),
        isFetching: ref(purchaseStatusState.isFetching),
        error: ref(purchaseStatusState.hasError ? new Error('status failed') : null),
      }
    }

    return {
      data: ref({
        emoticonId: 9,
        name: 'Novi',
        creatorId: 2,
        isActive: true,
        price: 100,
        tags: [],
        images: [{ imageId: 91, imageUrl: '/novi.png', sortOrder: 0 }],
        createdAt: '2026-05-01T10:00:00',
        modifiedAt: '2026-05-01T10:00:00',
      }),
      isLoading: ref(false),
      error: ref(null),
    }
  }),
  useMutation: vi.fn((options: Record<string, unknown>) => {
    mutationOptions.push(options)
    return {
      mutate: purchaseMutate,
      isPending: ref(false),
    }
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getEmoticonData: vi.fn(),
    checkPurchaseStatusData: vi.fn(),
    purchaseEmoticon,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: { userId: 1 },
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: confirmPurchase,
  }),
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: Record<string, unknown>) => (
        params?.price === undefined ? key : `${key}:${params.price}`
      ),
    }),
  }
})

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn(),
}))

vi.mock('@/features/emoticon/useToggleEmoticonVisibility', () => ({
  useToggleEmoticonVisibility: () => ({
    mutate: vi.fn(),
    isPending: ref(false),
  }),
}))

vi.mock('lucide-vue-next', () => {
  const Icon = { template: '<i />' }
  return {
    ArrowLeft: Icon,
    ShoppingCart: Icon,
    Tag: Icon,
    Calendar: Icon,
    User: Icon,
    TrendingUp: Icon,
    Pencil: Icon,
    EyeOff: Icon,
    Eye: Icon,
  }
})

vi.mock('@/components/common/ui/BaseButton.vue', () => ({
  default: {
    props: ['disabled', 'variant', 'size'],
    emits: ['click'],
    template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}))

import EmoticonDetail from '../EmoticonDetail.vue'

describe('EmoticonDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mutationOptions.length = 0
    purchaseStatusState.hasStatus = true
    purchaseStatusState.available = true
    purchaseStatusState.isLoading = false
    purchaseStatusState.isFetching = false
    purchaseStatusState.hasError = false
    purchaseStatusState.purchased = false
    purchaseStatusState.price = 100
  })

  it('invalidates user point cache after purchase success', () => {
    mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    const purchaseMutation = mutationOptions[0]
    expect(purchaseMutation).toBeDefined()

    ;(purchaseMutation.onSuccess as () => void)()

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', expect.any(Object)] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', expect.any(Object), 'purchased'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'points'] })
  })

  it('disables purchase while purchase status is loading', () => {
    purchaseStatusState.isLoading = true

    const wrapper = mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    const purchaseButton = wrapper.findAll('button')
      .find((button) => button.text().includes('emoticon.purchase.button.buyWithPrice'))

    expect(purchaseButton?.attributes('disabled')).toBeDefined()
  })

  it.each([
    ['missing', false, true, false],
    ['unavailable', true, false, false],
    ['failed with stale data', true, true, true],
  ])('fails closed when purchase status is %s', (_label, hasStatus, available, hasError) => {
    purchaseStatusState.hasStatus = hasStatus
    purchaseStatusState.available = available
    purchaseStatusState.hasError = hasError

    const wrapper = mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    const purchaseButton = wrapper.findAll('button')
      .find((button) => button.text().includes('emoticon.purchase.button.unavailable'))

    expect(purchaseButton?.attributes('disabled')).toBeDefined()
  })

  it('shows unavailable before purchased when the item is no longer for sale', () => {
    purchaseStatusState.available = false
    purchaseStatusState.purchased = true

    const wrapper = mount(EmoticonDetail, {
      global: { stubs: { RouterLink: true } },
    })

    expect(wrapper.text()).toContain('emoticon.purchase.button.unavailable')
    expect(wrapper.text()).not.toContain('emoticon.purchase.button.purchased')
  })

  it('uses the API price in the purchase confirmation', async () => {
    purchaseStatusState.price = 275

    const wrapper = mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    const purchaseButton = wrapper.findAll('button')
      .find((button) => button.text().includes('emoticon.purchase.button.buyWithPrice:275'))

    await purchaseButton?.trigger('click')
    await flushPromises()

    expect(confirmPurchase).toHaveBeenCalledWith('emoticon.purchase.confirm:275')
    expect(purchaseMutate).toHaveBeenCalledOnce()
  })

  it('preserves a free price instead of falling back to 100', () => {
    purchaseStatusState.price = 0

    const wrapper = mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    expect(wrapper.text()).toContain('emoticon.purchase.button.buyWithPrice:0')
  })

  it('uses fluid image tiles without fixed pixel dimensions', () => {
    const wrapper = mount(EmoticonDetail, {
      global: {
        stubs: {
          RouterLink: true,
        },
      },
    })

    expect(wrapper.findAll('div').some((element) => (
      element.classes().includes('grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))]')
    ))).toBe(true)
    expect(wrapper.html()).not.toContain('width: 100px')
    expect(wrapper.html()).not.toContain('height: 100px')
  })
})
