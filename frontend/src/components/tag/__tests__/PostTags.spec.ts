import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'
import PostTags from '../PostTags.vue'

const mountPostTags = (props?: Record<string, unknown>) => {
    return mount(PostTags, {
        props: {
            modelValue: [],
            ...props,
        },
        global: {
            mocks: {
                $t: (key: string) => key,
            },
            stubs: {
                RouterLink: RouterLinkStub,
            },
        },
    })
}

describe('PostTags', () => {
    it('adds a trimmed tag and clears input on enter', async () => {
        const wrapper = mountPostTags({ modelValue: ['alpha'] })
        const input = wrapper.find('input')

        await input.setValue(' beta ')
        await input.trigger('keydown.enter')

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['alpha', 'beta']])
        expect((input.element as HTMLInputElement).value).toBe('')
    })

    it('does not emit for empty or duplicate tags', async () => {
        const wrapper = mountPostTags({ modelValue: ['alpha'] })
        const input = wrapper.find('input')

        await input.setValue('alpha')
        await input.trigger('keydown.enter')
        await input.setValue('   ')
        await input.trigger('keydown.enter')

        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('removes tag by index when remove button is clicked', async () => {
        const wrapper = mountPostTags({ modelValue: ['alpha', 'beta'] })
        const removeButtons = wrapper.findAll('button').filter((button) => button.find('.sr-only').exists())

        await removeButtons[0].trigger('click')

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['beta']])
    })

    it('renders readonly tags as links when boardUrl is provided', () => {
        const wrapper = mountPostTags({
            readOnly: true,
            boardUrl: 'free',
            modelValue: ['news'],
        })
        const link = wrapper.findComponent(RouterLinkStub)

        expect(wrapper.find('input').exists()).toBe(false)
        expect(link.exists()).toBe(true)
        expect(link.props('to')).toEqual({
            path: '/board/free',
            query: { q: 'news', type: 'TAG' },
        })
    })

    it('renders readonly tags as plain text when boardUrl is missing', () => {
        const wrapper = mountPostTags({
            readOnly: true,
            modelValue: ['plain'],
        })

        expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
        expect(wrapper.text()).toContain('#plain')
    })

    it('renders readonly compact tags without the default badge background', () => {
        const wrapper = mountPostTags({
            compact: true,
            readOnly: true,
            boardUrl: 'free',
            modelValue: ['compact'],
        })
        const link = wrapper.findComponent(RouterLinkStub)

        expect(link.classes()).toContain('text-[11px]')
        expect(link.classes()).not.toContain('bg-blue-100')
        expect(link.classes()).toContain('bg-slate-100')
        expect(link.classes()).toContain('rounded-full')
    })
})
