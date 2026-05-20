import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import PostEditorAdvancedPopover from '../PostEditorAdvancedPopover.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('lucide-vue-next', () => {
    const icon = defineComponent({
        name: 'TestIcon',
        setup: () => () => h('i'),
    })
    return {
        SeparatorHorizontal: icon,
        Table2: icon,
    }
})

function mountPopover(props = {}) {
    return mount(PostEditorAdvancedPopover, {
        props: {
            showTablePopover: false,
            ...props,
        },
    })
}

describe('PostEditorAdvancedPopover', () => {
    it('emits table and horizontal rule actions with trigger elements', async () => {
        const wrapper = mountPopover()
        const tableButton = wrapper.get('button[aria-controls="editor-table-dialog"]')

        await tableButton.trigger('click')
        await wrapper.get('button[title="board.writePost.toolbar.divider"]').trigger('click')

        expect(wrapper.emitted('open-table')?.[0][0]).toBe(tableButton.element)
        expect(wrapper.emitted('horizontal-rule')).toHaveLength(1)
    })

    it('exposes dialog aria controls and expanded state from props', () => {
        const wrapper = mountPopover({
            showTablePopover: true,
        })
        const tableButton = wrapper.get('button[aria-controls="editor-table-dialog"]')

        expect(tableButton.attributes('aria-haspopup')).toBe('dialog')
        expect(tableButton.attributes('aria-controls')).toBe('editor-table-dialog')
        expect(tableButton.attributes('aria-expanded')).toBe('true')
    })

    it('keeps formatting controls out of the advanced popover', () => {
        const wrapper = mountPopover()

        expect(wrapper.find('select.tiptap-select').exists()).toBe(false)
        expect(wrapper.find('.tiptap-color-trigger').exists()).toBe(false)
        expect(wrapper.find('button[title="board.writePost.alignCenter"]').exists()).toBe(false)
    })
})
