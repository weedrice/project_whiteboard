import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'

vi.mock('@/features/shop/useShop', () => ({ useMyPurchases: vi.fn() }))
vi.mock('@/composables/usePaginatedListState', () => ({
  usePaginatedListState: () => ({
    page: ref(0),
    size: ref(15),
    handlePageChange: vi.fn(),
    handleSizeChange: vi.fn(),
    items: ref([
      {
        purchaseId: 1,
        item: { itemId: 11, itemName: 'Without image', imageUrl: null },
        price: 100,
        purchasedAt: '2026-07-14T10:00:00',
      },
      {
        purchaseId: 2,
        item: { itemId: 12, itemName: 'With image', imageUrl: '/pack.png' },
        price: 250,
        purchasedAt: '2026-07-13T10:00:00',
      },
    ]),
    totalPages: ref(1),
    isLoading: ref(false),
    errorMessage: ref(''),
    refetch: vi.fn(),
  }),
}))

const ListCardStub = defineComponent({ setup(_props, { slots }) { return () => h('section', slots.default?.()) } })
vi.mock('lucide-vue-next', () => ({ ShoppingBag: { template: '<i />' } }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

import PurchaseHistory from '../PurchaseHistory.vue'

describe('PurchaseHistory', () => {
  it('renders backend purchase fields and only shows provided images', () => {
    const wrapper = mount(PurchaseHistory, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { PaginatedListCard: ListCardStub },
      },
    })

    expect(wrapper.text()).toContain('Without image')
    expect(wrapper.text()).toContain('100 P')
    expect(wrapper.text()).toContain('With image')
    expect(wrapper.text()).toContain('250 P')
    expect(wrapper.findAll('img')).toHaveLength(1)
    expect(wrapper.get('img').attributes('src')).toBe('/pack.png')
  })
})
