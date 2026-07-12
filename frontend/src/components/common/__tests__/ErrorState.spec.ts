import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, markRaw } from 'vue'
import ErrorState from '../ui/ErrorState.vue'

const messages: Record<string, string> = {
  'common.messages.defaultTitle': '오류가 발생했습니다',
  'common.error.defaultMessage': '요청하신 페이지를 처리하는 중 문제가 발생했습니다.',
  'common.error.retry': '다시 시도',
  'common.error.goHome': '홈으로 돌아가기'
}

describe('ErrorState', () => {
  it('renders the default icon without invoking it as a prop factory', () => {
    const wrapper = mount(ErrorState, {
      global: {
        mocks: {
          $t: (key: string) => messages[key] ?? key
        }
      }
    })

    expect(wrapper.find('svg').exists()).toBe(true)
  })

  it('renders default localized copy and action buttons', () => {
    const TestIcon = markRaw(defineComponent({
      setup: () => () => h('svg')
    }))

    const wrapper = mount(ErrorState, {
      props: {
        icon: TestIcon,
        showRetry: true,
        showGoHome: true
      },
      global: {
        mocks: {
          $t: (key: string) => messages[key] ?? key
        }
      }
    })

    expect(wrapper.text()).toContain('오류가 발생했습니다')
    expect(wrapper.text()).toContain('요청하신 페이지를 처리하는 중 문제가 발생했습니다.')
    expect(wrapper.text()).toContain('다시 시도')
    expect(wrapper.text()).toContain('홈으로 돌아가기')
    expect(wrapper.text()).not.toContain('common.error.title')
    expect(wrapper.text()).not.toContain('common.retry')
  })

  it('supports the page heading level and announces failures assertively', () => {
    const wrapper = mount(ErrorState, {
      props: { title: 'Page unavailable', titleTag: 'h1' },
      global: { mocks: { $t: (key: string) => messages[key] ?? key } }
    })

    expect(wrapper.get('h1').text()).toBe('Page unavailable')
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.attributes('aria-live')).toBe('assertive')
  })
})
