import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { usePostEditorInstance } from '../usePostEditorInstance'

describe('usePostEditorInstance accessibility', () => {
    it('labels the real editable DOM and updates its translation without replacing content or keyboard handlers', async () => {
        setActivePinia(createPinia())
        const label = ref('내용')
        let editor!: ReturnType<typeof usePostEditorInstance>
        const wrapper = mount(defineComponent({
            setup() {
                editor = usePostEditorInstance({
                    modelValue: ref('<p>Preserved draft</p>'),
                    accessibleLabel: label,
                    onUpdateHtml: vi.fn(),
                    openSlashMenu: vi.fn(),
                })
                return () => h('div')
            },
        }))
        try {
            await nextTick()
            const instance = editor.value!
            const element = instance.view.dom
            const keydown = instance.options.editorProps.handleDOMEvents?.keydown
            expect(element.getAttribute('role')).toBe('textbox')
            expect(element.getAttribute('aria-label')).toBe('내용')
            expect(element.getAttribute('aria-multiline')).toBe('true')

            label.value = 'Content'
            await nextTick()

            expect(element.getAttribute('role')).toBe('textbox')
            expect(element.getAttribute('aria-label')).toBe('Content')
            expect(element.getAttribute('aria-multiline')).toBe('true')
            expect(element.classList.contains('nv-rich-content')).toBe(true)
            expect(instance.options.editorProps.handleDOMEvents?.keydown).toBe(keydown)
            expect(instance.getHTML()).toBe('<p>Preserved draft</p>')
        } finally {
            wrapper.unmount()
        }
    })
})
