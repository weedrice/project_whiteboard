import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmoticonPickerStatePanel from '../EmoticonPickerStatePanel.vue'

describe('EmoticonPickerStatePanel', () => {
  it('announces empty content politely', () => {
    const wrapper = mount(EmoticonPickerStatePanel, {
      props: { state: 'empty', message: 'No emoticons' },
      global: { stubs: { Smile: true } },
    })

    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe('polite')
    expect(wrapper.text()).toContain('No emoticons')
  })

  it('announces errors and exposes touch-sized recovery actions', async () => {
    const wrapper = mount(EmoticonPickerStatePanel, {
      props: { state: 'error', message: 'Failed', retryLabel: 'Retry', backLabel: 'Back' },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Failed')
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons.every((button) => button.classes().includes('min-h-11'))).toBe(true)
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    expect(wrapper.emitted('back')).toHaveLength(1)
  })
})
