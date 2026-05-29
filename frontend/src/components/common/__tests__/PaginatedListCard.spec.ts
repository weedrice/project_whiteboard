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

const mountCard = () => mount(PaginatedListCard, {
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
  },
  slots: {
    default: '<div data-testid="content">Content</div>',
  },
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
})
