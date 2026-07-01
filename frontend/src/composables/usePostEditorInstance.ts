import { watch, type Ref } from 'vue'
import { useEditor, type Editor } from '@tiptap/vue-3'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'

type ImageAltPopoverOpener = (target: HTMLImageElement, alt: string, nodePos: number) => void

export function usePostEditorInstance(options: {
    modelValue: Ref<string>
    onUpdateHtml: (html: string) => void
    openSlashMenu: () => void
    openImageAltPopover: ImageAltPopoverOpener
}) {
    const editor = useEditor({
        content: options.modelValue.value || '',
        editable: true,
        editorProps: {
            attributes: {
                class: 'nv-rich-content prose prose-sm dark:prose-invert max-w-none min-h-[280px] px-4 py-4 focus:outline-none',
            },
            handleDOMEvents: {
                click: (_view, event) => {
                    const link = (event.target as HTMLElement)?.closest?.('a[href]')
                    if (!link) return false
                    if (event.ctrlKey || event.metaKey) return false
                    event.preventDefault()
                    return true
                },
                keydown: (_view, event) => {
                    const instance = editor.value
                    if (!instance || event.key !== '/') {
                        return false
                    }
                    const selection = instance.state.selection
                    if (!selection?.$from) {
                        return false
                    }
                    const isCollapsedSelection = selection.from === selection.to
                    const parentText = selection.$from.parent?.textContent ?? ''
                    const textBeforeCursor = parentText.slice(0, selection.$from.parentOffset)
                    const shouldOpenSlashMenu = isCollapsedSelection
                        && parentText.trim().length === 0
                        && textBeforeCursor.trim().length === 0

                    if (shouldOpenSlashMenu) {
                        event.preventDefault()
                        options.openSlashMenu()
                        return true
                    }
                    return false
                },
            },
            handleClickOn: (_view, _pos, node, nodePos, event) => {
                if (node.type.name !== 'image') return false
                const target = event.target instanceof HTMLElement ? event.target.closest('img') : null
                if (!(target instanceof HTMLImageElement)) return false
                options.openImageAltPopover(target, node.attrs.alt ?? '', nodePos)
                return false
            },
        },
        extensions: createPostEditorExtensions(),
        onUpdate: ({ editor: instance }) => {
            options.onUpdateHtml(instance.getHTML())
        },
    })

    watch(
        options.modelValue,
        (value) => {
            if (!editor.value) return
            const current = editor.value.getHTML()
            if (value !== current) {
                editor.value.commands.setContent(value || '', { emitUpdate: false })
            }
        },
    )

    return editor
}

export function focusPostEditorAtPointer(editor: Editor, event: MouseEvent) {
    const currentTarget = event.currentTarget as HTMLElement
    const target = event.target as Node
    if (!currentTarget.contains(target)) return

    const view = editor.view
    const isClickOnEditorRoot = view.dom === target || view.dom.contains(target)
    const position = view.posAtCoords({ left: event.clientX, top: event.clientY })
    if (position != null) {
        if (!isClickOnEditorRoot) event.preventDefault()
        view.focus()
        editor.commands.setTextSelection(position.pos)
        return
    }
    const size = editor.state.doc.content.size
    if (size > 0) {
        event.preventDefault()
        view.focus()
        editor.commands.setTextSelection(Math.max(0, size - 1))
    }
}
