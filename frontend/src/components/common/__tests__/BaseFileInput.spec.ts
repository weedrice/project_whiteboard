import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaseFileInput from '../ui/BaseFileInput.vue'

const mountFileInput = (props = {}) => mount(BaseFileInput, {
    props,
    global: {
        mocks: {
            $t: (key: string) => key,
        },
    },
})

describe('BaseFileInput', () => {
    it('associates the visible label with the file input', () => {
        const wrapper = mountFileInput({
            id: 'avatar-upload',
            label: 'Avatar',
        })

        expect(wrapper.get('label').attributes('for')).toBe('avatar-upload')
        expect(wrapper.get('input[type="file"]').attributes('id')).toBe('avatar-upload')
    })

    it('connects error text to the file input for assistive technology', () => {
        const wrapper = mountFileInput({
            id: 'document-upload',
            error: 'Upload failed',
        })
        const input = wrapper.get('input[type="file"]')

        expect(input.attributes('aria-invalid')).toBe('true')
        expect(input.attributes('aria-describedby')).toBe('document-upload-error')
        expect(wrapper.get('#document-upload-error').attributes('role')).toBe('alert')
        expect(wrapper.get('#document-upload-error').text()).toBe('Upload failed')
    })

    it('emits the selected file on change', async () => {
        const wrapper = mountFileInput()
        const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
        const input = wrapper.get('input[type="file"]')

        Object.defineProperty(input.element, 'files', {
            value: [file],
            configurable: true,
        })

        await input.trigger('change')

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([file])
        expect(wrapper.emitted('change')).toHaveLength(1)
    })
})
