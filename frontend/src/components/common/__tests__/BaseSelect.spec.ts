import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSelect from '../ui/BaseSelect.vue'
import { getExposedVm } from '@/test/vue-test-helpers'

type BaseSelectProps = InstanceType<typeof BaseSelect>['$props']

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
        expect(wrapper.get('[role="alert"]').text()).toBe('Required')
        expect(wrapper.get('[role="alert"]').classes()).toContain('nv-form-error')
        expect(wrapper.get('select').classes()).toContain('is-invalid')
        expect(wrapper.get('select').attributes('aria-invalid')).toBe('true')
        expect(wrapper.get('select').attributes('aria-describedby')).toBe('test-select-error')
        expect(wrapper.get('#test-select-error').attributes('role')).toBe('alert')
    })

    it('generates one stable id for label and error attributes when id is omitted', () => {
        const wrapper = mount(BaseSelect, {
            props: {
                label: 'Category',
                error: 'Required',
            },
        })

        const selectId = wrapper.get('select').attributes('id')
        expect(selectId).toBeTruthy()
        expect(wrapper.get('label').attributes('for')).toBe(selectId)
        expect(wrapper.get('[role="alert"]').attributes('id')).toBe(`${selectId}-error`)
        expect(wrapper.get('select').attributes('aria-describedby')).toBe(`${selectId}-error`)
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
        expect(options[0].attributes('selected')).toBeUndefined()
        expect(options[1].text()).toBe('One')
        expect(options[2].text()).toBe('Two')
    })

    it('preserves numeric option values when emitting updates', async () => {
        const wrapper = mount(BaseSelect, {
            props: {
                modelValue: 10,
                options: [10, 20, 50],
            },
        })

        await wrapper.get('select').setValue('20')

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([20])
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
                options: null,
            } as unknown as BaseSelectProps,
            slots: {
                default: '<option value="fallback">Fallback</option>',
            },
        })

        const exposed = getExposedVm<{ normalizedOptions: unknown[] }>(wrapper)
        expect(exposed.normalizedOptions).toEqual([])
        expect(wrapper.find('option[value="fallback"]').exists()).toBe(true)
    })

    it('preserves external help text when an error is present', () => {
        const wrapper = mount(BaseSelect, {
            attrs: { 'aria-describedby': 'category-help' },
            props: { id: 'category', error: 'Required' },
        })

        expect(wrapper.get('select').attributes('aria-describedby')).toBe('category-help category-error')
    })
})
