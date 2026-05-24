import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostEditorColorPopover from '../PostEditorColorPopover.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

const colors = ['#111111', '#ff0000', '#00ff00']
const labels = {
    '#111111': 'Black',
    '#ff0000': 'Red',
    '#00ff00': 'Green',
}

function mountPopover(props = {}) {
    return mount(PostEditorColorPopover, {
        props: {
            colors,
            labels,
            currentTextColor: '#ff0000',
            isDefaultColor: false,
            ...props,
        },
    })
}

describe('PostEditorColorPopover', () => {
    it('exposes selected state for default and preset color buttons', async () => {
        const wrapper = mountPopover()
        const defaultButton = wrapper.get('.color-panel-default')
        const swatches = wrapper.findAll('.color-panel-swatch')

        expect(defaultButton.attributes('aria-pressed')).toBe('false')
        expect(swatches.map((swatch) => swatch.attributes('aria-pressed'))).toEqual(['false', 'true', 'false'])
        expect(swatches[1].attributes('aria-label')).toBe('Red')

        await wrapper.setProps({
            currentTextColor: '#111111',
            isDefaultColor: true,
        })

        expect(defaultButton.attributes('aria-pressed')).toBe('true')
        expect(swatches.map((swatch) => swatch.attributes('aria-pressed'))).toEqual(['true', 'false', 'false'])
    })

    it('keeps custom color input labelled and disables autocomplete', () => {
        const wrapper = mountPopover()
        const input = wrapper.get('#editor-custom-text-color')

        expect(wrapper.get('label[for="editor-custom-text-color"]').text()).toBe('board.writePost.toolbar.customColor')
        expect(input.attributes()).toMatchObject({
            name: 'editorCustomTextColor',
            autocomplete: 'off',
        })
        expect((input.element as HTMLInputElement).value).toBe('#ff0000')
    })

    it('emits color selection events without changing payloads', async () => {
        const wrapper = mountPopover()

        await wrapper.get('.color-panel-default').trigger('click')
        await wrapper.findAll('.color-panel-swatch')[2].trigger('click')
        await wrapper.get('#editor-custom-text-color').setValue('#123456')

        expect(wrapper.emitted('default-color')).toHaveLength(1)
        expect(wrapper.emitted('preset-color')).toEqual([['#00ff00']])
        expect(wrapper.emitted('custom-color')).toEqual([['#123456']])
    })
})
