import { nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PrivacyPolicy from '@/views/PrivacyPolicy.vue'
import TermsOfService from '@/views/TermsOfService.vue'

const locale = ref('ko')

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ locale }),
}))

describe('legal pages', () => {
  beforeEach(() => {
    locale.value = 'ko'
  })

  it('renders the privacy policy in the selected locale with semantic lists', async () => {
    const wrapper = mount(PrivacyPolicy)

    expect(wrapper.get('h1').text()).toBe('개인정보 처리방침')
    expect(wrapper.findAll('section').length).toBeGreaterThan(9)
    expect(wrapper.findAll('ul').length).toBeGreaterThan(0)

    locale.value = 'en'
    await nextTick()

    expect(wrapper.get('h1').text()).toBe('Privacy Policy')
    expect(wrapper.text()).toContain('Personal Information We Collect')
  })

  it('renders terms sections using ordered and unordered lists', () => {
    const wrapper = mount(TermsOfService, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })

    expect(wrapper.findAll('ol').length).toBeGreaterThan(0)
    expect(wrapper.findAll('ul').length).toBeGreaterThan(0)
    expect(wrapper.get('article').classes()).toContain('max-w-3xl')
  })
})
