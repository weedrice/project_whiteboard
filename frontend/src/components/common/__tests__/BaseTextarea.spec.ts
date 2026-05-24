import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaseTextarea from '../ui/BaseTextarea.vue'

describe('BaseTextarea', () => {
    it('keeps a screen-reader-only label when hideLabel is true', () => {
        const wrapper = mount(BaseTextarea, {
            props: {
                id: 'hidden-textarea',
                label: 'Hidden textarea',
                hideLabel: true,
            },
        })

        const label = wrapper.get('label')
        expect(label.text()).toBe('Hidden textarea')
        expect(label.attributes('for')).toBe('hidden-textarea')
        expect(label.classes()).toContain('sr-only')
    })

    it('connects error text to textarea accessibility attributes', () => {
        const wrapper = mount(BaseTextarea, {
            props: {
                id: 'bio',
                label: 'Bio',
                error: 'Bio is required',
            },
        })

        const textarea = wrapper.get('textarea')
        expect(textarea.attributes('aria-invalid')).toBe('true')
        expect(textarea.attributes('aria-describedby')).toBe('bio-error')
        expect(wrapper.get('#bio-error').text()).toBe('Bio is required')
        expect(wrapper.get('#bio-error').attributes('role')).toBe('alert')
    })

    it('emits update:modelValue on input', async () => {
        const wrapper = mount(BaseTextarea, {
            props: {
                modelValue: '',
            },
        })

        await wrapper.get('textarea').setValue('new value')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['new value'])
    })
})
