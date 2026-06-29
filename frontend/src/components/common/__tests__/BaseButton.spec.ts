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

    it('disables and marks the button busy while loading', async () => {
        const wrapper = mount(BaseButton, {
            props: {
                loading: true,
            },
            slots: {
                default: 'Saving',
            },
        })

        expect(wrapper.attributes('disabled')).toBeDefined()
        expect(wrapper.attributes('aria-busy')).toBe('true')
        expect(wrapper.text()).toContain('Saving')
        expect(wrapper.find('[role="status"]').exists()).toBe(true)

        await wrapper.trigger('click')

        expect(wrapper.emitted('click')).toBeUndefined()
    })

    it('applies large size class and full width class', () => {
        const wrapper = mount(BaseButton, {
            props: {
                size: 'lg',
                fullWidth: true,
            },
        })

        expect(wrapper.classes()).toContain('w-full')
        expect(wrapper.classes()).toContain('px-6')
        expect(wrapper.classes()).toContain('py-3')
        expect(wrapper.classes()).toContain('text-base')
    })

    it('falls back to primary variant for unknown variant values', () => {
        const wrapper = mount(BaseButton, {
            props: {
                variant: 'not-supported' as any,
            },
        })

        expect(wrapper.classes()).toContain('btn-primary')
    })
})
