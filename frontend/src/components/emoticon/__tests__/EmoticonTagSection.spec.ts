import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import EmoticonTagSection from '../EmoticonTagSection.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

describe('EmoticonTagSection IME input', () => {
  it('adds tags only after composition finishes and preserves the add button', async () => {
    const wrapper = mount(EmoticonTagSection, {
      props: { inputId: 'tag-input', modelValue: '한글 태그', tagItems: [], tagCount: 0 },
    })
    const input = wrapper.get('#tag-input')
    for (const ime of [{ isComposing: true }, { keyCode: 229 }]) {
      const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...ime })
      input.element.dispatchEvent(event)
      expect(event.defaultPrevented).toBe(false)
    }
    expect(wrapper.emitted('add')).toBeUndefined()
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    input.element.dispatchEvent(enter)
    expect(enter.defaultPrevented).toBe(true)
    expect(wrapper.emitted('add')).toHaveLength(1)
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('add')).toHaveLength(2)
    wrapper.unmount()
  })
})
