import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PageHeader from '../ui/PageHeader.vue'

describe('PageHeader', () => {
  it('renders the shared title, description, icon, and actions structure', () => {
    const wrapper = mount(PageHeader, {
      props: {
        title: 'Manage spaces',
        description: 'Configure this space.',
        size: 'compact',
      },
      slots: {
        icon: '<span data-test="icon" />',
        actions: '<button type="button">Back</button>',
      },
    })

    expect(wrapper.get('h1').text()).toBe('Manage spaces')
    expect(wrapper.get('h1').classes()).toEqual(expect.arrayContaining(['text-lg', 'font-semibold']))
    expect(wrapper.get('p').text()).toBe('Configure this space.')
    expect(wrapper.find('[data-test="icon"]').exists()).toBe(true)
    expect(wrapper.get('button').text()).toBe('Back')
  })

  it('applies the admin title scale while preserving named slots', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Dashboard', size: 'admin' },
      slots: {
        icon: '<span data-test="admin-icon" />',
        actions: '<button type="button">Refresh</button>',
      },
    })

    expect(wrapper.get('h1').classes()).toEqual(expect.arrayContaining(['text-xl', 'leading-7']))
    expect(wrapper.find('[data-test="admin-icon"]').exists()).toBe(true)
    expect(wrapper.get('button').text()).toBe('Refresh')
    expect(wrapper.find('p').exists()).toBe(false)
  })
})
