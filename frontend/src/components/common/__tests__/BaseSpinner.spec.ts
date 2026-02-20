import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSpinner from '../ui/BaseSpinner.vue'

describe('BaseSpinner', () => {
    it('renders default size and color classes', () => {
        const wrapper = mount(BaseSpinner)
        const spinner = wrapper.get('[role="status"]')

        expect(spinner.classes()).toContain('h-8')
        expect(spinner.classes()).toContain('w-8')
        expect(spinner.classes()).toContain('text-indigo-600')
    })

    it('applies custom size and color classes', () => {
        const wrapper = mount(BaseSpinner, {
            props: {
                size: 'lg',
                color: 'text-red-500',
            },
        })
        const spinner = wrapper.get('[role="status"]')

        expect(spinner.classes()).toContain('h-12')
        expect(spinner.classes()).toContain('w-12')
        expect(spinner.classes()).toContain('text-red-500')
    })
})
