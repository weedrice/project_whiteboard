import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AdminInlinePager from '../AdminInlinePager.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => ({
      'common.previous': '이전',
      'common.next': '다음',
    })[key] ?? key,
  }),
}))

describe('AdminInlinePager', () => {
  it('emits previous and next with boundary disabled states', async () => {
    const wrapper = mount(AdminInlinePager, {
      props: {
        page: 1,
        totalPages: 3,
      },
    })

    const buttons = wrapper.findAll('button')
    expect(buttons[0].text()).toBe('이전')
    expect(buttons[1].text()).toBe('다음')

    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('previous')).toHaveLength(1)
    expect(wrapper.emitted('next')).toHaveLength(1)
  })

  it('does not render when there are no pages', () => {
    const wrapper = mount(AdminInlinePager, {
      props: {
        page: 0,
        totalPages: 0,
      },
    })

    expect(wrapper.find('button').exists()).toBe(false)
  })
})
