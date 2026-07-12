import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MyPageNavigation from '../MyPageNavigation.vue'

const groups = [
  { nameKey: 'content', items: [{ nameKey: 'overview', href: '/mypage' }] },
  { nameKey: 'account', items: [{ nameKey: 'settings', href: '/mypage/settings' }] },
]

describe('MyPageNavigation', () => {
  it('renders grouped navigation and marks the nested route active', () => {
    const wrapper = mount(MyPageNavigation, {
      props: { groups, activePath: '/mypage/settings' },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { RouterLink: RouterLinkStub },
      },
    })

    expect(wrapper.text()).toContain('content')
    expect(wrapper.text()).toContain('account')
    const activeLinks = wrapper.findAll('[aria-current="page"]')
    expect(activeLinks).toHaveLength(2)
    expect(activeLinks.every((link) => link.text() === 'settings')).toBe(true)
    expect(wrapper.findAll('.lg\\:hidden summary').every((summary) => summary.classes().includes('min-h-11'))).toBe(true)
    expect(wrapper.findAll('.lg\\:hidden a').every((link) => link.classes().includes('min-h-11'))).toBe(true)
  })
})
