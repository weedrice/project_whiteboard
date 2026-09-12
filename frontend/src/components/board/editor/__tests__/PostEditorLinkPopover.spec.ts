import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import PostEditorLinkPopover from '../PostEditorLinkPopover.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

const BaseButtonStub = defineComponent({
    name: 'BaseButton',
    props: {
        type: { type: String, default: 'button' },
    },
    emits: ['click'],
    setup(props, { emit, slots }) {
        return () =>
            h(
                'button',
                {
                    type: props.type,
                    onClick: () => emit('click'),
                },
                slots.default?.(),
            )
    },
})

function mountPopover(props = {}) {
    return mount(PostEditorLinkPopover, {
        props: {
            url: 'https://example.com',
            text: 'Example',
            canRemove: true,
            ...props,
        },
        global: {
            stubs: {
                BaseButton: BaseButtonStub,
            },
        },
    })
}

describe('PostEditorLinkPopover', () => {
    it.each(['editor-link-url', 'editor-link-text'])('preserves IME Enter/Escape in %s and handles normal keys', async (id) => {
        const wrapper = mountPopover()
        const input = wrapper.get(`#${id}`)
        const parentKeydown = vi.fn()
        input.element.parentElement!.addEventListener('keydown', parentKeydown)
        for (const ime of [{ isComposing: true }, { keyCode: 229 }]) {
            for (const key of ['Enter', 'Escape']) {
                const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...ime })
                input.element.dispatchEvent(event)
                expect(event.defaultPrevented).toBe(false)
            }
        }
        expect(wrapper.emitted('apply')).toBeUndefined()
        expect(wrapper.emitted('close')).toBeUndefined()
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
        input.element.dispatchEvent(enter)
        expect(enter.defaultPrevented).toBe(true)
        expect(wrapper.emitted('apply')).toEqual([['https://example.com', 'Example']])
        const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
        input.element.dispatchEvent(escape)
        expect(escape.defaultPrevented).toBe(true)
        expect(wrapper.emitted('close')).toHaveLength(1)
        expect(parentKeydown).not.toHaveBeenCalled()
        wrapper.unmount()
    })

    it('keeps url and text local state in sync with props and emits apply payloads', async () => {
        const wrapper = mountPopover()
        const inputs = wrapper.findAll('.link-popover-input')

        expect(wrapper.get('label[for="editor-link-url"]').text()).toBe('board.writePost.linkUrlPrompt')
        expect(wrapper.get('#editor-link-url').attributes()).toMatchObject({
            name: 'editorLinkUrl',
            inputmode: 'url',
            autocomplete: 'off',
        })
        expect(wrapper.get('label[for="editor-link-text"]').text()).toBe('board.writePost.linkDisplayText')
        expect(wrapper.get('#editor-link-text').attributes()).toMatchObject({
            name: 'editorLinkText',
            autocomplete: 'off',
        })
        expect((inputs[0].element as HTMLInputElement).value).toBe('https://example.com')
        expect((inputs[1].element as HTMLInputElement).value).toBe('Example')

        await inputs[0].setValue('https://noviis.kr/post/1')
        await inputs[1].setValue('Noviis')
        await inputs[0].trigger('keydown', { key: 'Enter' })

        expect(wrapper.emitted('apply')).toEqual([['https://noviis.kr/post/1', 'Noviis']])

        await wrapper.setProps({
            url: 'https://changed.test',
            text: 'Changed',
        })

        expect((inputs[0].element as HTMLInputElement).value).toBe('https://changed.test')
        expect((inputs[1].element as HTMLInputElement).value).toBe('Changed')

        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(wrapper.emitted('apply')?.at(-1)).toEqual(['https://changed.test', 'Changed'])
    })

    it('emits close from keyboard and cancel button', async () => {
        const wrapper = mountPopover()
        const inputs = wrapper.findAll('.link-popover-input')

        await inputs[1].trigger('keydown.escape')
        await wrapper.findAll('.link-popover-actions button')[0].trigger('click')

        expect(wrapper.emitted('close')).toHaveLength(2)
    })

    it('emits remove only when remove action is available', async () => {
        const wrapper = mountPopover()

        await wrapper.get('.link-popover-remove').trigger('click')
        expect(wrapper.emitted('remove')).toHaveLength(1)

        await wrapper.setProps({ canRemove: false })
        expect(wrapper.find('.link-popover-remove').exists()).toBe(false)
    })
})
