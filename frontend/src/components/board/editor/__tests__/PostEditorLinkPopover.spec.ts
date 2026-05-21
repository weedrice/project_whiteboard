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
    it('keeps url and text local state in sync with props and emits apply payloads', async () => {
        const wrapper = mountPopover()
        const inputs = wrapper.findAll('.link-popover-input')

        expect((inputs[0].element as HTMLInputElement).value).toBe('https://example.com')
        expect((inputs[1].element as HTMLInputElement).value).toBe('Example')

        await inputs[0].setValue('https://noviis.kr/post/1')
        await inputs[1].setValue('Noviis')
        await inputs[0].trigger('keydown.enter')

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
