import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaseBadge from '../ui/BaseBadge.vue'

describe('BaseBadge', () => {
  it.each([
    ['accent', 'bg-[var(--nv-accent)]'],
    ['outline', 'border-[var(--nv-line)]'],
  ] as const)('renders the %s variant through the shared badge API', (variant, expectedClass) => {
    const wrapper = mount(BaseBadge, {
      props: { variant },
      slots: { default: 'Badge' },
    })

    expect(wrapper.text()).toBe('Badge')
    expect(wrapper.classes()).toContain(expectedClass)
  })
})
