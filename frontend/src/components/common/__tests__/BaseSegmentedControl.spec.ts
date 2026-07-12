import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import BaseSegmentedControl from '../ui/BaseSegmentedControl.vue'

const options = [
  { value: 'id', label: 'Find ID', id: 'find-id-tab', controls: 'find-id-panel' },
  { value: 'password', label: 'Reset password', id: 'password-tab', controls: 'password-panel' },
  { value: 'email', label: 'Verify email', id: 'email-tab', controls: 'email-panel' },
]

function mountTabControl() {
  return mount(defineComponent({
    components: { BaseSegmentedControl },
    setup() {
      const model = ref('id')
      return { model, options }
    },
    template: `
      <BaseSegmentedControl
        v-model="model"
        :options="options"
        label="Account help"
        selection-mode="tab"
      />
    `,
  }), {
    attachTo: document.body,
  })
}

describe('BaseSegmentedControl', () => {
  it('allows translated labels to wrap without forcing the control wider than its container', () => {
    const wrapper = mount(BaseSegmentedControl, {
      props: {
        modelValue: 'id',
        options,
        label: 'Account help',
        variant: 'pill',
      },
    })

    expect(wrapper.get('[role="group"]').classes()).toEqual(expect.arrayContaining(['max-w-full', 'flex-wrap']))
    expect(wrapper.get('button').classes()).toEqual(expect.arrayContaining(['min-h-11', 'min-w-11', 'whitespace-normal']))
  })

  it('uses roving tabindex for tab selection mode', () => {
    const wrapper = mountTabControl()
    const tabs = wrapper.findAll('[role="tab"]')

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe('Account help')
    expect(tabs.map((tab) => tab.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
    expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual(['true', 'false', 'false'])
    expect(tabs.map((tab) => tab.attributes('id'))).toEqual(['find-id-tab', 'password-tab', 'email-tab'])
    expect(tabs.map((tab) => tab.attributes('aria-controls'))).toEqual(['find-id-panel', 'password-panel', 'email-panel'])
  })

  it('moves tab selection and focus with arrow, Home, and End keys', async () => {
    const wrapper = mountTabControl()

    await wrapper.findAll('[role="tab"]')[0].trigger('keydown', { key: 'ArrowRight' })
    let tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual(['false', 'true', 'false'])
    expect(document.activeElement).toBe(tabs[1].element)

    await tabs[1].trigger('keydown', { key: 'End' })
    tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual(['false', 'false', 'true'])
    expect(document.activeElement).toBe(tabs[2].element)

    await tabs[2].trigger('keydown', { key: 'Home' })
    tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual(['true', 'false', 'false'])
    expect(document.activeElement).toBe(tabs[0].element)
  })
})
