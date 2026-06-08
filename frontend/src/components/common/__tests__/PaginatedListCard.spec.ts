import { mount } from '@vue/test-utils'
import { defineComponent, h, markRaw } from 'vue'
import { describe, expect, it } from 'vitest'
import PaginatedListCard from '../ui/PaginatedListCard.vue'

const IconStub = defineComponent({
  name: 'IconStub',
  setup() {
    return () => h('span', { 'data-testid': 'icon' })
  },
})

type PaginatedListCardProps = InstanceType<typeof PaginatedListCard>['$props']

const mountCard = (
  props: Partial<PaginatedListCardProps> = {},
  slots: Record<string, string> = { default: '<div data-testid="content">Content</div>' },
) => mount(PaginatedListCard, {
  props: {
    title: 'Notifications',
    icon: markRaw(IconStub),
    itemsCount: 1,
    loading: false,
    error: null,
    emptyTitle: 'Empty',
    page: 0,
    size: 15,
    totalPages: 1,
    ...props,
  },
  slots,
  global: {
    stubs: {
      PageSizeSelector: true,
      Pagination: true,
      EmptyState: true,
      ErrorState: true,
    },
  },
})

describe('PaginatedListCard', () => {
  it('uses tokenized surfaces, borders, and title colors', () => {
    const wrapper = mountCard()

    expect(wrapper.find('.nv-surface').exists()).toBe(true)
    expect(wrapper.get('.nv-title').text()).toContain('Notifications')
    expect(wrapper.findAll('.nv-border').length).toBeGreaterThan(0)
    expect(wrapper.find('.nv-surface-muted').exists()).toBe(true)
  })

  it('renders an opt-in loading preset when no loading slot is provided', () => {
    const wrapper = mountCard({
      itemsCount: 0,
      loading: true,
      loadingPreset: 'post-list',
      loadingRows: 2,
    })

    expect(wrapper.find('[data-testid="content"]').exists()).toBe(false)
    expect(wrapper.findAll('.animate-pulse')).toHaveLength(6)
  })

  it('keeps custom loading slots ahead of loading presets', () => {
    const wrapper = mountCard(
      {
        itemsCount: 0,
        loading: true,
        loadingPreset: 'post-list',
      },
      {
        default: '<div data-testid="content">Content</div>',
        loading: '<div data-testid="custom-loading">Custom loading</div>',
      },
    )

    expect(wrapper.get('[data-testid="custom-loading"]').text()).toBe('Custom loading')
    expect(wrapper.find('.animate-pulse').exists()).toBe(false)
  })
})
