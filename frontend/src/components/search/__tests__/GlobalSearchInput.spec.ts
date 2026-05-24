import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GlobalSearchInput from '../GlobalSearchInput.vue'

const routerPush = vi.fn()

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: routerPush,
    }),
}))

describe('GlobalSearchInput', () => {
    it('keeps a hidden label for the search input', () => {
        const wrapper = mount(GlobalSearchInput)
        const label = wrapper.get('label')

        expect(label.classes()).toContain('sr-only')
        expect(label.text()).toBe('Search')
        expect(wrapper.get('input').attributes('placeholder')).toBe('Search...')
    })

    it('submits trimmed keywords and clears the input', async () => {
        const wrapper = mount(GlobalSearchInput)
        const input = wrapper.get('input')

        await input.setValue('  vue  ')
        await input.trigger('keydown.enter')

        expect(routerPush).toHaveBeenCalledWith({ name: 'search', query: { q: 'vue' } })
        expect((input.element as HTMLInputElement).value).toBe('')
    })
})
