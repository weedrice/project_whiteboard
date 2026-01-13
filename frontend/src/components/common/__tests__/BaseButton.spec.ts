import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from '../ui/BaseButton.vue'

describe('BaseButton', () => {
    it('renders slot content', () => {
        const wrapper = mount(BaseButton, {
            slots: {
                default: 'Click Me'
            }
        })
        expect(wrapper.text()).toBe('Click Me')
    })

    it('emits click event', async () => {
        const wrapper = mount(BaseButton)
        await wrapper.trigger('click')
        expect(wrapper.emitted('click')).toBeTruthy()
    })

    it('applies variant class', () => {
        const wrapper = mount(BaseButton, {
            props: {
                variant: 'danger'
            }
        })
        expect(wrapper.classes()).toContain('btn-danger')
    })

    it('disables button when disabled prop is true', () => {
        const wrapper = mount(BaseButton, {
            props: {
                disabled: true
            }
        })
        expect(wrapper.attributes('disabled')).toBeDefined()
        expect(wrapper.classes()).toContain('opacity-50')
    })
})
