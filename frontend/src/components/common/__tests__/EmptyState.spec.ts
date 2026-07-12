import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../ui/EmptyState.vue'

describe('EmptyState', () => {
  it('uses h3 by default and supports a contextual title level', () => {
    const defaultWrapper = mount(EmptyState, {
      props: { title: 'No rows' },
      global: { mocks: { $t: (key: string) => key }, stubs: { RouterLink: true } },
    })
    const contextualWrapper = mount(EmptyState, {
      props: { title: 'No search results', titleTag: 'h2' },
      global: { mocks: { $t: (key: string) => key }, stubs: { RouterLink: true } },
    })

    expect(defaultWrapper.get('h3').text()).toBe('No rows')
    expect(contextualWrapper.get('h2').text()).toBe('No search results')
  })
})
