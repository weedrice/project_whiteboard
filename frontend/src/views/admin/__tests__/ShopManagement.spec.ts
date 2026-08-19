import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShopManagement from '../ShopManagement.vue'

const shopItemsData = ref({
  content: [
    {
      itemId: 10,
      itemName: '테스트 이모티콘',
      description: '설명',
      price: 100,
      itemType: 'EMOTICON',
      targetId: 20,
      imageUrl: null,
      isActive: true,
      isSaleEnabled: true,
      purchasable: true,
      createdAt: '2026-08-19T00:00:00',
      modifiedAt: '2026-08-19T00:00:00',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
})
const updateSaleStatus = vi.fn()
const isUpdatePending = ref(false)
const addToast = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast }),
}))

vi.mock('@/features/admin/shop/useAdminShopItems', () => ({
  useAdminShopItems: () => ({
    data: shopItemsData,
    isLoading: ref(false),
  }),
  useUpdateAdminShopItemSaleStatus: () => ({
    mutateAsync: updateSaleStatus,
    isPending: isUpdatePending,
  }),
}))

const AdminPaginatedTableStub = defineComponent({
  props: {
    items: { type: Array, default: () => [] },
  },
  template: `
    <div>
      <div v-for="item in items" :key="item.itemId" data-testid="shop-item-row">
        <slot name="cell-itemName" :item="item" />
        <slot name="cell-saleStatus" :item="item" />
        <slot name="cell-actions" :item="item" />
      </div>
    </div>
  `,
})

const BaseTextareaStub = defineComponent({
  props: {
    modelValue: { type: String, default: '' },
    error: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  template: `
    <div>
      <textarea :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />
      <p v-if="error" role="alert">{{ error }}</p>
    </div>
  `,
})

const mountShopManagement = () => mount(ShopManagement, {
  global: {
    stubs: {
      PauseCircle: true,
      PlayCircle: true,
      AdminDataPage: {
        template: '<section><slot name="filters" /><slot /></section>',
      },
      AdminFilterPanel: {
        template: '<section><slot /></section>',
      },
      AdminFilterField: {
        template: '<label><slot /></label>',
      },
      AdminFilterActions: {
        template: '<button type="button">filters</button>',
      },
      AdminPaginatedTable: AdminPaginatedTableStub,
      AdminStatusBadge: {
        props: ['label'],
        template: '<span data-testid="sale-status">{{ label }}</span>',
      },
      AdminTableActions: {
        template: '<div><slot /></div>',
      },
      AdminActionButton: {
        props: ['label', 'disabled'],
        emits: ['click'],
        template: '<button type="button" :aria-label="label" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
      },
      BaseInput: {
        props: ['modelValue'],
        template: '<input :value="modelValue" />',
      },
      BaseSelect: {
        props: ['modelValue'],
        template: '<select :value="modelValue" />',
      },
      BaseModal: {
        props: ['isOpen'],
        template: '<section v-if="isOpen" data-testid="sale-status-modal"><slot /><footer><slot name="footer" /></footer></section>',
      },
      BaseTextarea: BaseTextareaStub,
      BaseButton: {
        props: ['disabled'],
        emits: ['click'],
        template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
      },
    },
  },
})

describe('ShopManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    updateSaleStatus.mockResolvedValue(undefined)
    isUpdatePending.value = false
    shopItemsData.value.content[0].isActive = true
    shopItemsData.value.content[0].isSaleEnabled = true
  })

  it('shows the independent sale status and opens the suspend dialog', async () => {
    const wrapper = mountShopManagement()

    expect(wrapper.get('[data-testid="shop-item-row"]').text()).toContain('테스트 이모티콘')
    expect(wrapper.get('[data-testid="sale-status"]').text()).toBe('admin.shop.status.onSale')

    await wrapper.get('[aria-label="admin.shop.actions.suspend"]').trigger('click')

    expect(wrapper.find('[data-testid="sale-status-modal"]').exists()).toBe(true)
  })

  it('requires a reason before suspending sales', async () => {
    const wrapper = mountShopManagement()
    await wrapper.get('[aria-label="admin.shop.actions.suspend"]').trigger('click')
    await wrapper.get('textarea').setValue('   ')

    const suspendButtons = wrapper.findAll('button')
      .filter(button => button.text().includes('admin.shop.actions.suspend'))
    await suspendButtons.at(-1)!.trigger('click')

    expect(updateSaleStatus).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('admin.shop.messages.reasonRequired')
  })

  it('keeps an admin suspension visible when the source is also inactive', () => {
    shopItemsData.value.content[0].isActive = false
    shopItemsData.value.content[0].isSaleEnabled = false

    const wrapper = mountShopManagement()

    expect(wrapper.get('[data-testid="sale-status"]').text()).toBe('admin.shop.status.suspended')
  })

  it('trims the reason and requests a sale suspension', async () => {
    const wrapper = mountShopManagement()
    await wrapper.get('[aria-label="admin.shop.actions.suspend"]').trigger('click')
    await wrapper.get('textarea').setValue('  운영 정책 위반  ')

    const suspendButtons = wrapper.findAll('button')
      .filter(button => button.text().includes('admin.shop.actions.suspend'))
    await suspendButtons.at(-1)!.trigger('click')

    expect(updateSaleStatus).toHaveBeenCalledWith({
      itemId: 10,
      saleEnabled: false,
      reason: '운영 정책 위반',
    })
    expect(addToast).toHaveBeenCalledWith('admin.shop.messages.suspended', 'success')
  })
})
