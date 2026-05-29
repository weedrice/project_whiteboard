import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaseCard from '../ui/BaseCard.vue'

describe('BaseCard', () => {
  it('uses the shared surface token on the card shell', () => {
    const wrapper = mount(BaseCard, {
      slots: {
        default: '<p>Card content</p>',
      },
    })

    expect(wrapper.text()).toContain('Card content')
    expect(wrapper.classes()).toContain('nv-surface')
    expect(wrapper.classes()).not.toContain('bg-white')
    expect(wrapper.classes()).not.toContain('dark:bg-gray-800')
  })
})
