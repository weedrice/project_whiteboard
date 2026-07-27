import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import i18n, { setAppLocale } from '@/i18n'
import { useBadgeTranslation } from '@/features/user/useBadgeTranslation'

const BadgeTranslationHost = defineComponent({
  props: {
    badgeCode: { type: String, required: true },
    name: { type: String, required: true },
    description: { type: String, required: true },
  },
  setup(props) {
    const { badgeName, badgeDescription } = useBadgeTranslation()
    return { badgeName, badgeDescription }
  },
  template: `
    <div>
      <span data-name>{{ badgeName({ badgeCode, name }) }}</span>
      <span data-description>{{ badgeDescription({ badgeCode, name, description }) }}</span>
    </div>
  `,
})

const mountBadge = (badgeCode: string) => mount(BadgeTranslationHost, {
  props: {
    badgeCode,
    name: 'API badge name',
    description: 'API badge description',
  },
})

describe('useBadgeTranslation', () => {
  afterEach(async () => {
    await setAppLocale('ko')
  })

  it('shows the Korean badge name and description in the Korean locale', () => {
    const wrapper = mountBadge('FIRST_POST')

    expect(wrapper.get('[data-name]').text()).toBe('첫 게시글')
    expect(wrapper.get('[data-description]').text()).toBe('첫 번째 게시글을 작성하면 획득합니다.')
  })

  it('shows the English badge name and description in the English locale', async () => {
    await setAppLocale('en')
    const wrapper = mountBadge('FIRST_POST')

    expect(wrapper.get('[data-name]').text()).toBe('First Post')
    expect(wrapper.get('[data-description]').text()).toBe('Earned after writing your first post.')
  })

  it('uses the API text when the badge code has no translation', () => {
    const wrapper = mountBadge('FUTURE_BADGE')

    expect(wrapper.get('[data-name]').text()).toBe('API badge name')
    expect(wrapper.get('[data-description]').text()).toBe('API badge description')
  })
})
