import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import AdminPaginationFooter from '../AdminPaginationFooter.vue'

const PaginationStub = defineComponent({
  props: {
    currentPage: Number,
    totalPages: Number,
  },
  emits: ['page-change'],
  template: '<button data-test="pagination" @click="$emit(\'page-change\', 2)">{{ currentPage }}/{{ totalPages }}</button>',
})

describe('AdminPaginationFooter', () => {
  it('renders summary, description, actions, and forwards page changes', async () => {
    const wrapper = mount(AdminPaginationFooter, {
      props: {
        page: 0,
        totalPages: 3,
        summary: '총 3건',
      },
      global: {
        stubs: {
          Pagination: PaginationStub,
        },
      },
      slots: {
        description: '<p>double click rows</p>',
        actions: '<select><option>20</option></select>',
      },
    })

    expect(wrapper.text()).toContain('총 3건')
    expect(wrapper.text()).toContain('double click rows')
    expect(wrapper.find('select').exists()).toBe(true)

    await wrapper.get('[data-test="pagination"]').trigger('click')

    expect(wrapper.emitted('page-change')).toEqual([[2]])
  })
})
