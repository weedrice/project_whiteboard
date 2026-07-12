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

  it('supports semantic shells and visual variants', () => {
    const wrapper = mount(BaseCard, {
      props: {
        as: 'section',
        padding: 'lg',
        radius: 'xl',
        elevation: 'md',
        bordered: true,
      },
      slots: { default: 'Section content' },
    })

    expect(wrapper.element.tagName).toBe('SECTION')
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['rounded-xl', 'shadow-md', 'border']))
    expect(wrapper.get('section > div').classes()).toEqual(expect.arrayContaining(['p-5', 'sm:p-6']))
  })
})
