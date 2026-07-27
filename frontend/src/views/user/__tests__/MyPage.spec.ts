import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import MyPage from '../MyPage.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/mypage/badges' }),
}))

const navigationStub = {
  name: 'MyPageNavigation',
  props: ['groups', 'activePath'],
  template: '<nav />',
}

describe('MyPage', () => {
  it('includes the badge page in activity navigation', () => {
    const wrapper = shallowMount(MyPage, {
      global: {
        stubs: {
          MyPageNavigation: navigationStub,
          RouterView: true,
        },
      },
    })

    const navigation = wrapper.getComponent(navigationStub)
    const groups = navigation.props('groups') as Array<{
      nameKey: string
      items: Array<{ nameKey: string; href: string }>
    }>
    const activity = groups.find((group) => group.nameKey === 'user.navigation.activity')

    expect(activity?.items).toContainEqual({
      nameKey: 'user.badges.title',
      href: '/mypage/badges',
    })
    expect(navigation.props('activePath')).toBe('/mypage/badges')
  })
})
