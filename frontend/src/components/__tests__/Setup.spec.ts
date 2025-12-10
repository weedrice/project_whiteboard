import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

describe('Setup Test', () => {
    it('sanity check', () => {
        expect(true).toBe(true)
    })

    it('mounts a component', () => {
        const wrapper = mount({
            template: '<div>Hello Vitest</div>'
        })
        expect(wrapper.text()).toContain('Hello Vitest')
    })
})
