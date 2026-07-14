import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'

const routerPush = vi.hoisted(() => vi.fn())
const addToast = vi.hoisted(() => vi.fn())
const confirmPurchase = vi.hoisted(() => vi.fn(async () => true))
const mutateAsync = vi.hoisted(() => vi.fn(async () => undefined))
const authState = vi.hoisted(() => ({
  isAuthenticated: true,
  user: { userId: 1, loginId: 'novi', points: 500 },
}))
const pointState = vi.hoisted(() => ({ currentPoint: 500 }))
const handlePageChange = vi.hoisted(() => vi.fn())
const translate = vi.hoisted(() => (key: string, params?: Record<string, unknown>) => {
  if (key === 'shop.purchaseConfirm') return `${params?.name}:${params?.price}`
  if (key === 'shop.insufficientPoints') return `${params?.current}/${params?.price}`
  return key
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/shop?page=1' }),
  useRouter: () => ({ push: routerPush }),
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: confirmPurchase }) }))
vi.mock('@/composables/useUser', () => ({
  useUser: () => ({ useMyPoint: () => ({ data: ref({ currentPoint: pointState.currentPoint }) }) }),
}))
vi.mock('@/features/shop/useShop', () => ({
  useShopItems: vi.fn(),
  usePurchaseShopItem: () => ({ mutateAsync }),
}))
vi.mock('@/composables/usePaginatedListState', () => ({
  usePaginatedListState: () => ({
    page: ref(0),
    handlePageChange,
    items: ref([{
      itemId: 7,
      itemName: 'Novi pack',
      description: 'A pack',
      price: 100,
      itemType: 'EMOTICON',
    }]),
    totalPages: ref(3),
    isLoading: ref(false),
    errorMessage: ref(''),
    refetch: vi.fn(),
  }),
}))
vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: (error: unknown) => error instanceof Error ? error.message : '',
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: translate }) }))

const ButtonStub = defineComponent({
  props: ['disabled', 'loading'],
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', {
      disabled: props.disabled,
      'data-loading': String(Boolean(props.loading)),
      onClick: () => emit('click'),
    }, slots.default?.())
  },
})

const CardStub = defineComponent({ setup(_props, { slots }) { return () => h('div', slots.default?.()) } })
const PaginationStub = defineComponent({
  props: ['currentPage', 'totalPages'],
  emits: ['page-change'],
  template: '<button data-test="next-page" @click="$emit(\'page-change\', 1)">next</button>',
})
vi.mock('lucide-vue-next', () => ({ Coins: { template: '<i />' }, ShoppingBag: { template: '<i />' } }))

import ShopPage from '../ShopPage.vue'

const mountPage = () => mount(ShopPage, {
  global: {
    mocks: { $t: translate },
    stubs: {
      BaseButton: ButtonStub,
      BaseCard: CardStub,
      BaseBadge: CardStub,
      BaseSkeleton: true,
      EmptyState: true,
      ErrorState: true,
      Pagination: PaginationStub,
    },
  },
})

describe('ShopPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.isAuthenticated = true
    authState.user.points = 500
    pointState.currentPoint = 500
    mutateAsync.mockResolvedValue(undefined)
  })

  it('returns an anonymous buyer to the same shop after login', async () => {
    authState.isAuthenticated = false
    const wrapper = mountPage()

    await wrapper.get('button').trigger('click')

    expect(routerPush).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/shop?page=1' } })
    expect(mutateAsync).not.toHaveBeenCalled()
  })

  it('confirms and purchases an affordable item', async () => {
    const wrapper = mountPage()

    await wrapper.get('button').trigger('click')
    await nextTick()

    expect(confirmPurchase).toHaveBeenCalledWith('Novi pack:100')
    expect(mutateAsync).toHaveBeenCalledWith(7)
    expect(addToast).toHaveBeenCalledWith('shop.purchaseSuccess', 'success')
  })

  it('shows a backend business error from a rejected purchase', async () => {
    mutateAsync.mockRejectedValueOnce(new Error('이미 소유한 상품입니다.'))
    const wrapper = mountPage()

    await wrapper.get('button').trigger('click')
    await nextTick()

    expect(addToast).toHaveBeenCalledWith('이미 소유한 상품입니다.', 'error')
  })

  it('blocks an unaffordable purchase before confirmation', async () => {
    pointState.currentPoint = 50
    const wrapper = mountPage()

    await wrapper.get('button').trigger('click')

    expect(addToast).toHaveBeenCalledWith('50/100', 'error')
    expect(confirmPurchase).not.toHaveBeenCalled()
    expect(mutateAsync).not.toHaveBeenCalled()
  })

  it('passes a valid next page to the shared pagination state', async () => {
    const wrapper = mountPage()

    await wrapper.get('[data-test="next-page"]').trigger('click')

    expect(handlePageChange).toHaveBeenCalledWith(1)
    expect(Number.isNaN(handlePageChange.mock.calls[0]?.[0])).toBe(false)
  })
})
