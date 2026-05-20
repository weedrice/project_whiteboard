import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PostEditorSlashMenu from '../PostEditorSlashMenu.vue'

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

const actions = ['heading', 'image', 'quote', 'list', 'link', 'table', 'video', 'codeBlock', 'divider'] as const

function mountMenu(activeIndex = 1) {
    return mount(PostEditorSlashMenu, {
        props: {
            actions: [...actions],
            activeIndex,
        },
        attachTo: document.body,
    })
}

describe('PostEditorSlashMenu', () => {
    it('uses roving tabindex and focuses the active item when props change', async () => {
        const wrapper = mountMenu(1)
        await nextTick()

        const buttons = wrapper.findAll('.slash-action-btn')
        expect(buttons.map((button) => button.attributes('tabindex'))).toEqual([
            '-1',
            '0',
            '-1',
            '-1',
            '-1',
            '-1',
            '-1',
            '-1',
            '-1',
        ])
        expect(document.activeElement).toBe(buttons[1].element)

        await wrapper.setProps({ activeIndex: 3 })
        await nextTick()

        expect(wrapper.findAll('.slash-action-btn').map((button) => button.attributes('tabindex'))).toEqual([
            '-1',
            '-1',
            '-1',
            '0',
            '-1',
            '-1',
            '-1',
            '-1',
            '-1',
        ])
        expect(document.activeElement).toBe(wrapper.findAll('.slash-action-btn')[3].element)
    })

    it('emits keyboard navigation and selection events', async () => {
        const wrapper = mountMenu(2)
        const menu = wrapper.get('[role="menu"]')
        await nextTick()
        const setActiveOffset = wrapper.emitted('set-active')?.length ?? 0

        await menu.trigger('keydown', { key: 'ArrowDown' })
        await menu.trigger('keydown', { key: 'ArrowUp' })
        await menu.trigger('keydown', { key: 'Home' })
        await menu.trigger('keydown', { key: 'End' })
        await menu.trigger('keydown', { key: 'Enter' })
        await menu.trigger('keydown', { key: 'Escape' })

        expect(wrapper.emitted('move')).toEqual([[1], [-1]])
        expect(wrapper.emitted('set-active')?.slice(setActiveOffset)).toEqual([[0], [8]])
        expect(wrapper.emitted('select')).toEqual([['quote']])
        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('emits set-active on hover and focus and select on click', async () => {
        const wrapper = mountMenu(0)
        await nextTick()
        const setActiveOffset = wrapper.emitted('set-active')?.length ?? 0
        const buttons = wrapper.findAll('.slash-action-btn')

        await buttons[3].trigger('mouseenter')
        await buttons[4].trigger('focus')
        await buttons[1].trigger('click')

        expect(wrapper.emitted('set-active')?.slice(setActiveOffset)).toEqual([[3], [4]])
        expect(wrapper.emitted('select')).toEqual([['image']])
    })
})
