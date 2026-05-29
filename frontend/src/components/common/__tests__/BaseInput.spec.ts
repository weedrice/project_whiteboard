import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseInput from '../ui/BaseInput.vue'

describe('BaseInput', () => {
    it('renders label when provided', () => {
        const wrapper = mount(BaseInput, {
            props: {
                label: 'Test Label',
                id: 'test-input'
            }
        })
        expect(wrapper.find('label').text()).toBe('Test Label')
    })

    it('keeps a screen-reader-only label when hideLabel is true', () => {
        const wrapper = mount(BaseInput, {
            props: {
                id: 'hidden-label-input',
                label: 'Hidden Label',
                hideLabel: true,
            },
        })

        const label = wrapper.get('label')
        expect(label.text()).toBe('Hidden Label')
        expect(label.attributes('for')).toBe('hidden-label-input')
        expect(label.classes()).toContain('sr-only')
    })

    it('emits update:modelValue on input', async () => {
        const wrapper = mount(BaseInput, {
            props: {
                modelValue: ''
            }
        })
        const input = wrapper.find('input')
        await input.setValue('new value')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['new value'])
    })

    it('displays error message when provided', () => {
        const wrapper = mount(BaseInput, {
            props: {
                error: 'Invalid input'
            }
        })
        expect(wrapper.get('[role="alert"]').text()).toBe('Invalid input')
        expect(wrapper.get('[role="alert"]').classes()).toContain('nv-form-error')
        expect(wrapper.get('input').classes()).toContain('is-invalid')
    })

    it('disables input when disabled prop is true', () => {
        const wrapper = mount(BaseInput, {
            props: {
                disabled: true
            }
        })
        expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    })

    it('keeps suffix slot accessible for assistive technology', () => {
        const wrapper = mount(BaseInput, {
            slots: {
                suffix: '<button type="button" aria-label="Clear search">x</button>'
            }
        })

        const suffixContainer = wrapper.findAll('div').find((node) =>
            node.classes().includes('absolute') && node.classes().includes('right-0')
        )

        expect(suffixContainer).toBeDefined()
        expect(suffixContainer?.attributes('aria-hidden')).toBeUndefined()
        expect(wrapper.find('button[aria-label=\"Clear search\"]').exists()).toBe(true)
    })

    it('emits blur and renders prefix slot as aria-hidden decoration', async () => {
        const wrapper = mount(BaseInput, {
            props: {
                id: 'search-input',
            },
            slots: {
                prefix: '<span data-testid="prefix-icon">icon</span>',
            },
        })

        const input = wrapper.get('input')
        await input.trigger('blur')

        const prefixContainer = wrapper.findAll('div').find((node) =>
            node.classes().includes('absolute') && node.classes().includes('left-0'),
        )
        expect(prefixContainer?.attributes('aria-hidden')).toBe('true')
        expect(wrapper.find('[data-testid="prefix-icon"]').exists()).toBe(true)
        expect(wrapper.emitted('blur')).toHaveLength(1)
    })
})
