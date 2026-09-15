import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SearchSidebarPanel from '../SearchSidebarPanel.vue'

const mountPanel = (props: Record<string, unknown> = {}) => mount(SearchSidebarPanel, {
  props: { title: 'Popular', ...props },
  slots: {
    actions: '<button type="button">Clear</button>',
    default: '<p data-test="content">Results</p>',
  },
  global: {
    mocks: { $t: (key: string) => key },
  },
})

describe('SearchSidebarPanel', () => {
  it('renders its title, actions, and body slot in the ready state', () => {
    const wrapper = mountPanel()

    expect(wrapper.get('h2').text()).toBe('Popular')
    expect(wrapper.get('button').text()).toBe('Clear')
    expect(wrapper.get('[data-test="content"]').text()).toBe('Results')
  })

  it('renders loading, error, and empty states without leaking the body slot', async () => {
    const wrapper = mountPanel({ loading: true })
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="content"]').exists()).toBe(false)

    await wrapper.setProps({ loading: false, error: true, errorText: 'Unavailable' })
    expect(wrapper.get('[role="alert"]').text()).toContain('Unavailable')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)

    await wrapper.setProps({ error: false, empty: true, emptyText: 'Nothing here' })
    expect(wrapper.get('[role="status"]').text()).toContain('Nothing here')
    expect(wrapper.find('[data-test="content"]').exists()).toBe(false)
  })
})
