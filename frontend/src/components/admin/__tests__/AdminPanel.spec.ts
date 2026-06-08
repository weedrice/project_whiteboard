import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminPanel from '../AdminPanel.vue'

describe('AdminPanel', () => {
  it('renders content with the default panel classes', () => {
    const wrapper = mount(AdminPanel, {
      slots: {
        default: '<p>Panel content</p>',
      },
    })

    expect(wrapper.text()).toContain('Panel content')
    expect(wrapper.classes()).toContain('nv-surface')
    expect(wrapper.classes()).toContain('nv-border')
    expect(wrapper.classes()).toContain('p-5')
    expect(wrapper.classes()).toContain('shadow')
    expect(wrapper.classes()).toContain('rounded-lg')
  })

  it('supports compact padding, max width, and shadow opt out', () => {
    const wrapper = mount(AdminPanel, {
      props: {
        padding: 'sm',
        shadow: false,
        maxWidthClass: 'max-w-xl',
      },
    })

    expect(wrapper.classes()).toContain('p-4')
    expect(wrapper.classes()).toContain('max-w-xl')
    expect(wrapper.classes()).not.toContain('shadow')
  })

  it('keeps parent layout classes on the root panel', () => {
    const wrapper = mount(AdminPanel, {
      attrs: {
        class: 'mt-6',
      },
    })

    expect(wrapper.classes()).toContain('mt-6')
  })

  it('can render without panel padding for wrapped table surfaces', () => {
    const wrapper = mount(AdminPanel, {
      props: {
        padding: 'none',
      },
    })

    expect(wrapper.classes()).not.toContain('p-4')
    expect(wrapper.classes()).not.toContain('p-5')
  })

  it('supports small shadow, transparent border, and hidden overflow', () => {
    const wrapper = mount(AdminPanel, {
      props: {
        shadow: 'sm',
        border: 'transparent',
        overflow: 'hidden',
      },
    })

    expect(wrapper.classes()).toContain('shadow-sm')
    expect(wrapper.classes()).not.toContain('shadow')
    expect(wrapper.classes()).toContain('border-transparent')
    expect(wrapper.classes()).toContain('overflow-hidden')
  })
})
