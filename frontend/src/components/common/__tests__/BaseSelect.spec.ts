import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSelect from '../ui/BaseSelect.vue'

describe('BaseSelect', () => {
    it('renders label and error text', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                id: 'test-select',
                label: 'Category',
                error: 'Required',
            },
        })

        expect(wrapper.find('label[for="test-select"]').text()).toBe('Category')
        expect(wrapper.find('p.text-red-600').text()).toBe('Required')
        expect(wrapper.get('select').attributes('aria-invalid')).toBe('true')
        expect(wrapper.get('select').attributes('aria-describedby')).toBe('test-select-error')
        expect(wrapper.get('#test-select-error').attributes('role')).toBe('alert')
    })

    it('normalizes primitive options and emits updated value', async () => {
        const wrapper = mount(BaseSelect, {
            props: {
                modelValue: '',
                options: ['A', 2],
            },
        })

        const options = wrapper.findAll('option')
        expect(options).toHaveLength(2)
        expect(options[0].text()).toBe('A')
        expect(options[0].attributes('value')).toBe('A')
        expect(options[1].text()).toBe('2')
        expect(options[1].attributes('value')).toBe('2')

        await wrapper.find('select').setValue('A')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['A'])
    })

    it('renders object options and placeholder', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                options: [
                    { label: 'One', value: 1 },
                    { label: 'Two', value: 2 },
                ],
                placeholder: 'Please choose',
            },
        })

        const options = wrapper.findAll('option')
        expect(options[0].text()).toBe('Please choose')
        expect(options[1].text()).toBe('One')
        expect(options[2].text()).toBe('Two')
    })

    it('renders slot options when options prop is empty', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                options: [],
            },
            slots: {
                default: '<option value="x">Custom</option>',
            },
        })

        expect(wrapper.find('option[value="x"]').exists()).toBe(true)
    })

    it('supports disabled and hideLabel props', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                id: 'hidden-select',
                label: 'Hidden',
                hideLabel: true,
                disabled: true,
            },
        })

        const label = wrapper.get('label')
        expect(label.text()).toBe('Hidden')
        expect(label.attributes('for')).toBe('hidden-select')
        expect(label.classes()).toContain('sr-only')
        expect(wrapper.find('select').attributes('disabled')).toBeDefined()
    })

    it('handles null options safely and falls back to slot content', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                options: null as unknown as never[],
            },
            slots: {
                default: '<option value="fallback">Fallback</option>',
            },
        })

        expect((wrapper.vm as unknown as { normalizedOptions: unknown[] }).normalizedOptions).toEqual([])
        expect(wrapper.find('option[value="fallback"]').exists()).toBe(true)
    })
})
