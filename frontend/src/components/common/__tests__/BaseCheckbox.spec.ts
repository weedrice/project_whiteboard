import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseCheckbox from '../ui/BaseCheckbox.vue'

describe('BaseCheckbox', () => {
    it('renders label and description', () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                id: 'terms',
                label: 'Accept terms',
                description: 'You must agree',
            },
        })

        expect(wrapper.find('label[for="terms"]').text()).toBe('Accept terms')
        expect(wrapper.find('p').text()).toBe('You must agree')
    })

    it('connects description text with aria-describedby', () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                id: 'terms',
                label: 'Accept terms',
                description: 'You must agree',
            },
        })

        expect(wrapper.get('input').attributes('aria-describedby')).toBe('terms-description')
        expect(wrapper.get('#terms-description').text()).toBe('You must agree')
    })

    it('generates one stable id for label linkage when id is omitted', () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                label: 'Accept terms',
            },
        })

        const checkboxId = wrapper.get('input').attributes('id')
        expect(checkboxId).toBeTruthy()
        expect(wrapper.get('label').attributes('for')).toBe(checkboxId)
    })

    it('emits boolean value in single mode', async () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                modelValue: false,
            },
        })

        const input = wrapper.find('input[type="checkbox"]')
        await input.setValue(true)

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([true])
    })

    it('supports checked state from boolean modelValue', () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                modelValue: true,
            },
        })

        expect((wrapper.find('input').element as HTMLInputElement).checked).toBe(true)
    })

    it('adds value in array mode when checked', async () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                modelValue: ['alpha'],
                value: 'beta',
            },
        })

        await wrapper.find('input').setValue(true)
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['alpha', 'beta']])
    })

    it('removes value in array mode when unchecked', async () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                modelValue: ['alpha', 'beta'],
                value: 'beta',
            },
        })

        await wrapper.find('input').setValue(false)
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['alpha']])
    })

    it('supports disabled state', () => {
        const wrapper = mount(BaseCheckbox, {
            props: {
                disabled: true,
            },
        })

        expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    })
})
