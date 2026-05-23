import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ComponentCustomProperties } from 'vue'
import BaseSpinner from '../ui/BaseSpinner.vue'

const globalProperties = {
    $t: (key: string) => key
} as unknown as ComponentCustomProperties & Record<string, unknown>

describe('BaseSpinner', () => {
    it('renders default size and color classes', () => {
        const wrapper = mount(BaseSpinner, {
            global: {
                config: {
                    globalProperties
                }
            }
        })
        const spinner = wrapper.get('[role="status"]')

        expect(spinner.classes()).toContain('h-8')
        expect(spinner.classes()).toContain('w-8')
        expect(spinner.classes()).toContain('text-indigo-600')
        expect(wrapper.text()).toContain('common.loading')
    })

    it('applies custom size and color classes', () => {
        const wrapper = mount(BaseSpinner, {
            props: {
                size: 'lg',
                color: 'text-red-500',
            },
            global: {
                config: {
                    globalProperties
                }
            }
        })
        const spinner = wrapper.get('[role="status"]')

        expect(spinner.classes()).toContain('h-12')
        expect(spinner.classes()).toContain('w-12')
        expect(spinner.classes()).toContain('text-red-500')
    })
})
