import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostFormMetadataPanel from '../PostFormMetadataPanel.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

describe('PostFormMetadataPanel IME input', () => {
  it.each(['mobile', 'desktop'] as const)('creates a series only after composition finishes in %s', (layout) => {
    const wrapper = mount(PostFormMetadataPanel, {
      props: {
        layout, categories: [], seriesOptions: [], categoryId: '', seriesId: '',
        newSeriesTitle: '한글 시리즈', isCreatingSeries: false, tags: [],
        isNotice: false, isNsfw: false, isSpoiler: false, isSecret: false,
      },
      global: { mocks: { $t: (key: string) => key } },
    })
    const input = wrapper.get(layout === 'mobile' ? '#new-series-mobile' : '#new-series')
    for (const ime of [{ isComposing: true }, { keyCode: 229 }]) {
      const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...ime })
      input.element.dispatchEvent(event)
      expect(event.defaultPrevented).toBe(false)
    }
    expect(wrapper.emitted('create-series')).toBeUndefined()
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    input.element.dispatchEvent(enter)
    expect(enter.defaultPrevented).toBe(true)
    expect(wrapper.emitted('create-series')).toHaveLength(1)
    wrapper.unmount()
  })
})
