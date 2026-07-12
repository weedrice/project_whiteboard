import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
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

    it('labels the writable tag input', () => {
        const wrapper = mountPostTags({ inputId: 'custom-tag-input' })

        expect(wrapper.get('label[for="custom-tag-input"]').text()).toBe('board.tags.placeholder')
        expect(wrapper.get('#custom-tag-input').attributes()).toMatchObject({
            name: 'postTag',
            autocomplete: 'off',
        })
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

    it('does not add or clear a tag while IME composition is active', async () => {
        const wrapper = mountPostTags()
        const input = wrapper.find('input')

        await input.setValue('한글')
        await input.trigger('keydown.enter', { isComposing: true })

        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        expect((input.element as HTMLInputElement).value).toBe('한글')
    })

    it('removes tag by index when remove button is clicked', async () => {
        const wrapper = mountPostTags({ modelValue: ['alpha', 'beta'] })
        const removeButtons = wrapper.findAll('button').filter((button) => button.find('.sr-only').exists())

        expect(removeButtons[0].classes()).toEqual(expect.arrayContaining(['min-h-11', 'min-w-11']))

        await removeButtons[0].trigger('click')

        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['beta']])
    })

    it('emits tag-click for readonly tags when boardUrl is provided', async () => {
        const wrapper = mountPostTags({
            readOnly: true,
            boardUrl: 'free',
            modelValue: ['news'],
        })
        const tagButton = wrapper.get('button')

        expect(wrapper.find('input').exists()).toBe(false)
        expect(tagButton.classes()).toContain('min-h-11')
        await tagButton.trigger('click')

        expect(wrapper.emitted('tag-click')?.[0]).toEqual(['news'])
    })

    it('renders readonly tags as plain text when boardUrl is missing', () => {
        const wrapper = mountPostTags({
            readOnly: true,
            modelValue: ['plain'],
        })

        expect(wrapper.find('button').exists()).toBe(false)
        expect(wrapper.text()).toContain('#plain')
    })

    it('renders readonly compact tags without the default badge background', () => {
        const wrapper = mountPostTags({
            compact: true,
            readOnly: true,
            boardUrl: 'free',
            modelValue: ['compact'],
        })
        const tagButton = wrapper.get('button')

        expect(tagButton.classes()).toContain('text-xs')
        expect(tagButton.classes()).not.toContain('bg-blue-100')
        expect(tagButton.classes()).toContain('nv-surface-muted')
        expect(tagButton.classes()).toContain('rounded-full')
    })
})
