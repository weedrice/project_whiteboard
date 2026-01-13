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
        expect(wrapper.find('.text-red-600').text()).toBe('Invalid input')
    })

    it('disables input when disabled prop is true', () => {
        const wrapper = mount(BaseInput, {
            props: {
                disabled: true
            }
        })
        expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    })
})
