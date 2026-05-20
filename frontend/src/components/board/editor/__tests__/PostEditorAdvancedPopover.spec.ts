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
        TextAlignStart: icon,
        TextAlignCenter: icon,
        TextAlignEnd: icon,
        TextAlignJustify: icon,
        SeparatorHorizontal: icon,
        Table2: icon,
    }
})

function mountPopover(props = {}) {
    return mount(PostEditorAdvancedPopover, {
        props: {
            fontSizes: ['14px', '18px'],
            lineHeights: ['1.4', '1.8'],
            currentFontSize: '14px',
            currentLineHeight: '1.4',
            currentHighlightColor: '#fef08a',
            currentTextColor: '#111827',
            isDefaultColor: false,
            isDark: false,
            showColorPanel: false,
            showTablePopover: false,
            activeTextAlign: '',
            ...props,
        },
    })
}

describe('PostEditorAdvancedPopover', () => {
    it('emits font size, line height and highlight color changes', async () => {
        const wrapper = mountPopover()
        const selects = wrapper.findAll('select.tiptap-select')

        await selects[0].setValue('18px')
        await selects[1].setValue('1.8')
        await wrapper.get('input[type="color"]').setValue('#22c55e')

        expect(wrapper.emitted('font-size')).toEqual([['18px']])
        expect(wrapper.emitted('line-height')).toEqual([['1.8']])
        expect(wrapper.emitted('highlight-color')).toEqual([['#22c55e']])
    })

    it('emits color, table and horizontal rule actions with trigger elements', async () => {
        const wrapper = mountPopover()
        const colorButton = wrapper.get('.tiptap-color-trigger')
        const tableButton = wrapper.get('button[aria-controls="editor-table-dialog"]')

        await colorButton.trigger('click')
        await tableButton.trigger('click')
        await wrapper.get('button[title="board.writePost.toolbar.divider"]').trigger('click')

        expect(wrapper.emitted('toggle-color-panel')?.[0][0]).toBe(colorButton.element)
        expect(wrapper.emitted('open-table')?.[0][0]).toBe(tableButton.element)
        expect(wrapper.emitted('horizontal-rule')).toHaveLength(1)
    })

    it('exposes dialog aria controls and expanded state from props', () => {
        const wrapper = mountPopover({
            showColorPanel: true,
            showTablePopover: true,
        })
        const colorButton = wrapper.get('.tiptap-color-trigger')
        const tableButton = wrapper.get('button[aria-controls="editor-table-dialog"]')

        expect(colorButton.attributes('aria-haspopup')).toBe('dialog')
        expect(colorButton.attributes('aria-controls')).toBe('editor-color-dialog')
        expect(colorButton.attributes('aria-expanded')).toBe('true')
        expect(tableButton.attributes('aria-haspopup')).toBe('dialog')
        expect(tableButton.attributes('aria-controls')).toBe('editor-table-dialog')
        expect(tableButton.attributes('aria-expanded')).toBe('true')
    })

    it('emits align actions and marks the active align button', async () => {
        const wrapper = mountPopover({ activeTextAlign: 'center' })
        const alignButtons = [
            wrapper.get('button[title="board.writePost.alignLeft"]'),
            wrapper.get('button[title="board.writePost.alignCenter"]'),
            wrapper.get('button[title="board.writePost.alignRight"]'),
            wrapper.get('button[title="board.writePost.alignJustify"]'),
        ]

        expect(alignButtons[1].classes()).toContain('active')

        await alignButtons[0].trigger('click')
        await alignButtons[1].trigger('click')
        await alignButtons[2].trigger('click')
        await alignButtons[3].trigger('click')

        expect(wrapper.emitted('align')).toEqual([['left'], ['center'], ['right'], ['justify']])
    })

    it('renders default text color indicators for light and dark themes', async () => {
        const wrapper = mountPopover({
            isDefaultColor: true,
            isDark: false,
        })

        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('rgb(17, 24, 39)')

        await wrapper.setProps({ isDark: true })
        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('rgb(243, 244, 246)')
    })
})
