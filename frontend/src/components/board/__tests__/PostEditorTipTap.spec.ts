import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => {
    const chain = {
        focus: vi.fn(),
        toggleBold: vi.fn(),
        toggleItalic: vi.fn(),
        toggleUnderline: vi.fn(),
        toggleStrike: vi.fn(),
        setFontSize: vi.fn(),
        unsetFontSize: vi.fn(),
        setLineHeight: vi.fn(),
        unsetLineHeight: vi.fn(),
        unsetColor: vi.fn(),
        setColor: vi.fn(),
        setHighlight: vi.fn(),
        setLink: vi.fn(),
        insertContent: vi.fn(),
        extendMarkRange: vi.fn(),
        unsetLink: vi.fn(),
        insertTable: vi.fn(),
        setTextAlign: vi.fn(),
        setTextSelection: vi.fn(),
        toggleBulletList: vi.fn(),
        toggleOrderedList: vi.fn(),
        setHorizontalRule: vi.fn(),
        setImage: vi.fn(),
        setVideo: vi.fn(),
        run: vi.fn(),
    }
    chain.focus.mockImplementation(() => chain)
    chain.toggleBold.mockImplementation(() => chain)
    chain.toggleItalic.mockImplementation(() => chain)
    chain.toggleUnderline.mockImplementation(() => chain)
    chain.toggleStrike.mockImplementation(() => chain)
    chain.setFontSize.mockImplementation(() => chain)
    chain.unsetFontSize.mockImplementation(() => chain)
    chain.setLineHeight.mockImplementation(() => chain)
    chain.unsetLineHeight.mockImplementation(() => chain)
    chain.unsetColor.mockImplementation(() => chain)
    chain.setColor.mockImplementation(() => chain)
    chain.setHighlight.mockImplementation(() => chain)
    chain.setLink.mockImplementation(() => chain)
    chain.insertContent.mockImplementation(() => chain)
    chain.extendMarkRange.mockImplementation(() => chain)
    chain.unsetLink.mockImplementation(() => chain)
    chain.insertTable.mockImplementation(() => chain)
    chain.setTextAlign.mockImplementation(() => chain)
    chain.setTextSelection.mockImplementation(() => chain)
    chain.toggleBulletList.mockImplementation(() => chain)
    chain.toggleOrderedList.mockImplementation(() => chain)
    chain.setHorizontalRule.mockImplementation(() => chain)
    chain.setImage.mockImplementation(() => chain)
    chain.setVideo.mockImplementation(() => chain)
    chain.run.mockImplementation(() => true)

    const editor = {
        getHTML: vi.fn(() => '<p>editor-html</p>'),
        chain: vi.fn(() => chain),
        commands: {
            setContent: vi.fn(),
            setTextSelection: vi.fn(),
        },
        getAttributes: vi.fn((name: string): any => {
            if (name === 'textStyle') return { color: '', fontSize: '', lineHeight: '' }
            if (name === 'highlight') return { color: '#fef08a' }
            if (name === 'link') return { href: '' }
            return {}
        }),
        isActive: vi.fn((name: unknown) => name === 'link'),
        state: {
            selection: { from: 1, to: 1 },
            doc: {
                textBetween: vi.fn(() => 'selected text'),
                content: { size: 8 },
            },
        },
        view: {
            dom: document.createElement('div'),
            focus: vi.fn(),
            posAtCoords: vi.fn((): any => ({ pos: 3 })),
        },
        destroy: vi.fn(),
    }

    const editorRef = {
        __v_isRef: true as const,
        value: editor,
    }
    const editorOptions: { value: Record<string, unknown> | null } = { value: null }

    const toastAdd = vi.fn()
    const loggerError = vi.fn()
    const themeStore = { isDark: false }
    const i18nT = vi.fn((key: string) => key)

    const isUploadingImage = { __v_isRef: true as const, value: false }
    const validateImageFile = vi.fn(() => null as null | 'type' | 'size')
    const uploadImage = vi.fn()
    const isAbortUploadError = vi.fn(() => false)

    return {
        chain,
        editor,
        editorRef,
        editorOptions,
        toastAdd,
        loggerError,
        themeStore,
        i18nT,
        isUploadingImage,
        validateImageFile,
        uploadImage,
        isAbortUploadError,
    }
})

vi.mock('@tiptap/vue-3', () => ({
    useEditor: vi.fn((options: Record<string, unknown>) => {
        mocks.editorOptions.value = options
        return mocks.editorRef
    }),
    EditorContent: defineComponent({
        name: 'EditorContent',
        setup() {
            return () => h('div', { class: 'editor-content-stub' })
        },
    }),
}))

vi.mock('@tiptap/starter-kit', () => ({
    default: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-underline', () => ({ default: {} }))
vi.mock('@tiptap/extension-text-style', () => ({
    TextStyle: {},
    FontSize: { configure: vi.fn(() => ({})) },
    LineHeight: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-color', () => ({
    Color: { configure: vi.fn(() => ({})) },
}))
vi.mock('@tiptap/extension-highlight', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-link', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-image', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-text-align', () => ({ default: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-table', () => ({ TableKit: { configure: vi.fn(() => ({})) } }))
vi.mock('@tiptap/extension-horizontal-rule', () => ({ default: {} }))
vi.mock('@/extensions/tiptap-video', () => ({ Video: {} }))

vi.mock('lucide-vue-next', () => {
    const icon = defineComponent({ name: 'Icon', setup: () => () => h('i') })
    return {
        TextAlignStart: icon,
        TextAlignCenter: icon,
        TextAlignEnd: icon,
        TextAlignJustify: icon,
    }
})

vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: mocks.i18nT }),
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({ addToast: mocks.toastAdd }),
}))

vi.mock('@/stores/theme', () => ({
    useThemeStore: () => mocks.themeStore,
}))

vi.mock('@/composables/useEditorImageUpload', () => ({
    useEditorImageUpload: () => ({
        isUploadingImage: mocks.isUploadingImage,
        validateImageFile: mocks.validateImageFile,
        uploadImage: mocks.uploadImage,
        isAbortUploadError: mocks.isAbortUploadError,
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: { error: mocks.loggerError },
}))

import PostEditorTipTap from '../PostEditorTipTap.vue'

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

const mountEditor = (modelValue = '<p>initial</p>') => {
    return mount(PostEditorTipTap, {
        props: { modelValue },
        global: {
            stubs: {
                Teleport: true,
                BaseButton: BaseButtonStub,
            },
        },
    })
}

describe('PostEditorTipTap', () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    beforeEach(() => {
        vi.clearAllMocks()
        mocks.editorRef.value = mocks.editor
        mocks.themeStore.isDark = false
        mocks.editor.getHTML.mockReturnValue('<p>editor-html</p>')
        mocks.editor.state.selection = { from: 1, to: 1 }
        mocks.editor.state.doc.content.size = 8
        mocks.editor.view.posAtCoords.mockReturnValue({ pos: 3 })
        mocks.validateImageFile.mockReturnValue(null)
        mocks.uploadImage.mockResolvedValue({ url: 'https://cdn.test/image.png', fileId: 55 })
        mocks.isAbortUploadError.mockReturnValue(false)
        mocks.i18nT.mockImplementation((key: string) => key)
    })

    it('emits updated html and syncs external model changes', async () => {
        const wrapper = mountEditor('<p>old</p>')

        const onUpdate = mocks.editorOptions.value?.onUpdate as ({ editor }: { editor: typeof mocks.editor }) => void
        onUpdate({ editor: mocks.editor })
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['<p>editor-html</p>'])

        await wrapper.setProps({ modelValue: '<p>remote</p>' })
        expect(mocks.editor.commands.setContent).toHaveBeenCalledWith('<p>remote</p>', { emitUpdate: false })

        await wrapper.setProps({ modelValue: '' })
        expect(mocks.editor.commands.setContent).toHaveBeenCalledWith('', { emitUpdate: false })

        // Skip setContent when incoming value already equals current editor html.
        mocks.editor.commands.setContent.mockClear()
        await wrapper.setProps({ modelValue: '<p>editor-html</p>' })
        expect(mocks.editor.commands.setContent).not.toHaveBeenCalled()
    })

    it('handles link click dom events according to modifier keys', () => {
        mountEditor()
        const clickHandler = (mocks.editorOptions.value?.editorProps as any).handleDOMEvents.click

        const link = document.createElement('a')
        link.href = 'https://example.com'
        const noModifierEvent = {
            target: link,
            ctrlKey: false,
            metaKey: false,
            preventDefault: vi.fn(),
        } as unknown as MouseEvent
        expect(clickHandler(null, noModifierEvent)).toBe(true)
        expect(noModifierEvent.preventDefault).toHaveBeenCalled()

        const ctrlEvent = {
            target: link,
            ctrlKey: true,
            metaKey: false,
            preventDefault: vi.fn(),
        } as unknown as MouseEvent
        expect(clickHandler(null, ctrlEvent)).toBe(false)

        const metaEvent = {
            target: link,
            ctrlKey: false,
            metaKey: true,
            preventDefault: vi.fn(),
        } as unknown as MouseEvent
        expect(clickHandler(null, metaEvent)).toBe(false)

        const plainTargetEvent = {
            target: document.createElement('span'),
            ctrlKey: false,
            metaKey: false,
            preventDefault: vi.fn(),
        } as unknown as MouseEvent
        expect(clickHandler(null, plainTargetEvent)).toBe(false)
        expect(plainTargetEvent.preventDefault).not.toHaveBeenCalled()
    })

    it('applies and resets text colors from color panel', async () => {
        const wrapper = mountEditor()

        await wrapper.get('.tiptap-color-trigger').trigger('click')
        expect(wrapper.find('.color-panel').exists()).toBe(true)

        await wrapper.findAll('.color-panel-swatch')[0].trigger('click')
        expect(mocks.chain.setColor).toHaveBeenCalled()
        expect(wrapper.find('.color-panel').exists()).toBe(false)

        await wrapper.get('.tiptap-color-trigger').trigger('click')
        await wrapper.get('.link-popover-mask').trigger('click')
        expect(wrapper.find('.color-panel').exists()).toBe(false)

        await wrapper.get('.tiptap-color-trigger').trigger('click')
        await wrapper.get('.color-panel-default').trigger('click')
        expect(mocks.chain.unsetColor).toHaveBeenCalled()

        mocks.themeStore.isDark = true
        await wrapper.get('.tiptap-color-trigger').trigger('click')
        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('background-color')
    })

    it('opens link popover and applies or removes links', async () => {
        const wrapper = mountEditor()

        await wrapper.get('button[title="Link"]').trigger('click')
        expect(wrapper.find('.link-popover').exists()).toBe(true)

        const urlInput = wrapper.findAll('.link-popover-input')[0]
        await urlInput.setValue('')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.toastAdd).toHaveBeenCalledWith('board.writePost.linkUrlPrompt', 'error')

        await urlInput.setValue('example.com')
        const textInput = wrapper.findAll('.link-popover-input')[1]
        await textInput.setValue('Example')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('https://example.com'))

        mocks.chain.setLink.mockClear()
        mocks.editor.state.selection = { from: 2, to: 5 }
        await wrapper.get('button[title="Link"]').trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://selected.test')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.setLink).toHaveBeenCalledWith({ href: 'https://selected.test' })

        await wrapper.get('button[title="Link"]').trigger('click')
        await wrapper.find('.link-popover-remove').trigger('click')
        expect(mocks.chain.extendMarkRange).toHaveBeenCalledWith('link')
        expect(mocks.chain.unsetLink).toHaveBeenCalled()

        // Use URL as fallback display text when linkText is empty.
        mocks.editor.state.selection = { from: 1, to: 1 }
        await wrapper.get('button[title="Link"]').trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://fallback-text.test')
        await wrapper.findAll('.link-popover-input')[1].setValue('')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertContent).toHaveBeenLastCalledWith(expect.stringContaining('https://fallback-text.test'))
    })

    it('covers link/table fallback branches for i18n and selection guards', async () => {
        mocks.i18nT.mockImplementation((key: string) => {
            if (key === 'board.writePost.fontSize') return ''
            if (key === 'board.writePost.lineHeight') return ''
            if (key === 'board.writePost.linkUrlPrompt') return ''
            return key
        })
        mocks.editor.getAttributes.mockImplementation((name: string): any => {
            if (name === 'textStyle') return { color: '#22c55e', fontSize: '', lineHeight: '' }
            if (name === 'highlight') return { color: '#fef08a' }
            if (name === 'link') return undefined
            return {}
        })

        const wrapper = mountEditor()
        expect(wrapper.text()).toContain('크기')
        expect(wrapper.text()).toContain('줄간격')
        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('rgb(34, 197, 94)')

        mocks.editor.state.selection = undefined as unknown as { from: number; to: number }
        await wrapper.get('button[title="Link"]').trigger('click')
        expect((wrapper.findAll('.link-popover-input')[1].element as HTMLInputElement).value).toBe('')

        await wrapper.findAll('.link-popover-input')[0].setValue('')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.toastAdd).toHaveBeenLastCalledWith(expect.not.stringMatching(/^board\.writePost\.linkUrlPrompt$/), 'error')

        mocks.editor.state.selection = { from: 1, to: 2 }
        mocks.editor.state.doc.textBetween.mockReturnValueOnce(undefined as unknown as string)
        await wrapper.get('button[title="Link"]').trigger('click')
        expect((wrapper.findAll('.link-popover-input')[1].element as HTMLInputElement).value).toBe('')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://selected-text.test')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.setLink).toHaveBeenCalledWith({ href: 'https://selected-text.test' })

        mocks.editor.state.selection = undefined as unknown as { from: number; to: number }
        await wrapper.get('button[title="Link"]').trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://undefined-selection.test')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('https://undefined-selection.test'))

        await wrapper.get('button[title="Table"]').trigger('click')
        const numberInputs = wrapper.findAll('.table-popover .link-popover-input')
        await numberInputs[0].setValue('')
        await numberInputs[1].setValue('')
        await wrapper.findAll('.table-popover .link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertTable).toHaveBeenCalledWith({ rows: 3, cols: 3, withHeaderRow: true })
    })

    it('inserts clamped table config and list commands with saved selection', async () => {
        const wrapper = mountEditor()

        await wrapper.get('button[title="Table"]').trigger('click')
        const numberInputs = wrapper.findAll('.table-popover .link-popover-input')
        await numberInputs[0].setValue('99')
        await numberInputs[1].setValue('0')
        await wrapper.get('#table-header-row').setValue(false)
        await wrapper.findAll('.table-popover .link-popover-actions button').at(-1)!.trigger('click')

        expect(mocks.chain.insertTable).toHaveBeenCalledWith({ rows: 20, cols: 3, withHeaderRow: false })

        mocks.editor.state.selection = { from: 2, to: 6 }
        const bulletBtn = wrapper.find('button[title="Bullet list"]')
        await bulletBtn.trigger('mousedown')
        await bulletBtn.trigger('click')
        expect(mocks.chain.setTextSelection).toHaveBeenCalledWith({ from: 2, to: 6 })
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        mocks.editor.state.selection = { from: 1, to: 1 }
        await wrapper.get('button[title="Ordered list"]').trigger('click')
        expect(mocks.chain.toggleOrderedList).toHaveBeenCalled()

        // Save selection with collapsed cursor then apply bullet list fallback path.
        await bulletBtn.trigger('mousedown')
        await bulletBtn.trigger('click')
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        // Saved selection path for ordered list.
        mocks.editor.state.selection = { from: 4, to: 8 }
        const orderedBtn = wrapper.get('button[title="Ordered list"]')
        await orderedBtn.trigger('mousedown')
        await orderedBtn.trigger('click')
        expect(mocks.chain.setTextSelection).toHaveBeenCalledWith({ from: 4, to: 8 })
    })

    it('handles content area cursor placement from click coordinates and fallback', async () => {
        const wrapper = mountEditor()
        const contentArea = wrapper.get('.tiptap-content')
        const target = document.createElement('span')
        contentArea.element.appendChild(target)
        contentArea.element.appendChild(mocks.editor.view.dom)

        const firstEvent = new MouseEvent('mousedown', {
            bubbles: true,
            cancelable: true,
            clientX: 10,
            clientY: 20,
        })
        target.dispatchEvent(firstEvent)
        await nextTick()
        expect(mocks.editor.commands.setTextSelection).toHaveBeenCalledWith(3)

        const rootTargetEvent = new MouseEvent('mousedown', {
            bubbles: true,
            cancelable: true,
            clientX: 12,
            clientY: 22,
        })
        const rootPrevent = vi.spyOn(rootTargetEvent, 'preventDefault')
        mocks.editor.view.dom.dispatchEvent(rootTargetEvent)
        await nextTick()
        expect(rootPrevent).not.toHaveBeenCalled()

        mocks.editor.view.posAtCoords.mockReturnValueOnce(null as any)
        mocks.editor.state.doc.content.size = 10
        const secondEvent = new MouseEvent('mousedown', {
            bubbles: true,
            cancelable: true,
            clientX: 11,
            clientY: 21,
        })
        target.dispatchEvent(secondEvent)
        await nextTick()
        expect(mocks.editor.commands.setTextSelection).toHaveBeenCalledWith(9)

        mocks.editor.commands.setTextSelection.mockClear()
        mocks.editor.view.posAtCoords.mockReturnValueOnce(null as any)
        mocks.editor.state.doc.content.size = 0
        const thirdEvent = new MouseEvent('mousedown', {
            bubbles: true,
            cancelable: true,
            clientX: 13,
            clientY: 23,
        })
        target.dispatchEvent(thirdEvent)
        await nextTick()
        expect(mocks.editor.commands.setTextSelection).not.toHaveBeenCalled()
    })

    it('handles image upload validation, success and failure flows', async () => {
        const wrapper = mountEditor()
        const imageBtn = wrapper.get('button[title="Image"]')
        const fileInput = wrapper.get('input[type="file"]')
        const clickSpy = vi.spyOn(fileInput.element as HTMLInputElement, 'click')
        await imageBtn.trigger('click')
        expect(clickSpy).toHaveBeenCalled()

        const setFile = (name: string, type = 'image/png') => {
            const file = new File(['a'], name, { type })
            Object.defineProperty(fileInput.element, 'files', {
                value: [file],
                configurable: true,
            })
        }

        mocks.validateImageFile.mockReturnValueOnce('type')
        setFile('bad.txt', 'text/plain')
        await fileInput.trigger('change')
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.badRequest', 'warning')

        mocks.validateImageFile.mockReturnValueOnce('size')
        setFile('big.png')
        await fileInput.trigger('change')
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.fileSizeExceeded', 'warning')

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockResolvedValueOnce({ url: 'https://cdn.test/u1.png', fileId: 88 })
        setFile('ok.png')
        await fileInput.trigger('change')
        expect(mocks.chain.setImage).toHaveBeenCalledWith({ src: 'https://cdn.test/u1.png' })

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockResolvedValueOnce(null)
        setFile('none.png')
        await fileInput.trigger('change')

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockResolvedValueOnce({ url: 'https://cdn.test/non-number.png', fileId: 'x' } as unknown as {
            url: string
            fileId: number
        })
        setFile('string-id.png')
        await fileInput.trigger('change')
        expect(mocks.chain.setImage).toHaveBeenCalledWith({ src: 'https://cdn.test/non-number.png' })

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockRejectedValueOnce(new Error('abort'))
        mocks.isAbortUploadError.mockReturnValueOnce(true)
        setFile('abort.png')
        await fileInput.trigger('change')
        expect(mocks.loggerError).not.toHaveBeenCalledWith('Image upload failed:', expect.anything())

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockRejectedValueOnce(new Error('failed'))
        mocks.isAbortUploadError.mockReturnValueOnce(false)
        setFile('err.png')
        await fileInput.trigger('change')
        expect(mocks.loggerError).toHaveBeenCalledWith('Image upload failed:', expect.any(Error))
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.uploadFailed', 'error')
    })

    it('handles no-file image input and executes toolbar formatting commands', async () => {
        const wrapper = mountEditor()
        const fileInput = wrapper.get('input[type="file"]')
        Object.defineProperty(fileInput.element, 'files', {
            value: [],
            configurable: true,
        })
        await fileInput.trigger('change')
        expect(mocks.validateImageFile).not.toHaveBeenCalled()
        expect(mocks.uploadImage).not.toHaveBeenCalled()

        const clickWithMouseDown = async (selector: string) => {
            const node = wrapper.get(selector)
            await node.trigger('mousedown')
            await node.trigger('click')
        }
        await clickWithMouseDown('button[title="Bold"]')
        await clickWithMouseDown('button[title="Italic"]')
        await clickWithMouseDown('button[title="Underline"]')
        await clickWithMouseDown('button[title="Strikethrough"]')
        await clickWithMouseDown('button[title="Link"]')
        await clickWithMouseDown('button[title="Image"]')
        await clickWithMouseDown('button[title="Video"]')
        await clickWithMouseDown('button[title="Table"]')
        await clickWithMouseDown('button[title="board.writePost.alignLeft"]')
        await clickWithMouseDown('button[title="board.writePost.alignCenter"]')
        await clickWithMouseDown('button[title="board.writePost.alignRight"]')
        await clickWithMouseDown('button[title="board.writePost.alignJustify"]')
        await clickWithMouseDown('button[title="Horizontal rule"]')
        await clickWithMouseDown('button[title="Emoticon"]')

        const selects = wrapper.findAll('select.tiptap-select')
        await selects[0].setValue('18px')
        await selects[1].setValue('1.5')
        await selects[0].setValue('')
        await selects[1].setValue('')
        await wrapper.get('.tiptap-color-input').setValue('#22c55e')
        await wrapper.get('.tiptap-color-trigger').trigger('click')
        await wrapper.get('.color-panel-custom-input').setValue('#ff00aa')

        expect(mocks.chain.toggleBold).toHaveBeenCalled()
        expect(mocks.chain.toggleItalic).toHaveBeenCalled()
        expect(mocks.chain.toggleUnderline).toHaveBeenCalled()
        expect(mocks.chain.toggleStrike).toHaveBeenCalled()
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('left')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('center')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('right')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('justify')
        expect(mocks.chain.setHorizontalRule).toHaveBeenCalled()
        expect(mocks.chain.setFontSize).toHaveBeenCalledWith('18px')
        expect(mocks.chain.setLineHeight).toHaveBeenCalledWith('1.5')
        expect(mocks.chain.unsetFontSize).toHaveBeenCalled()
        expect(mocks.chain.unsetLineHeight).toHaveBeenCalled()
        expect(mocks.chain.setHighlight).toHaveBeenCalledWith({ color: '#22c55e' })
    })

    it('covers list and click guard branches through setupState helpers', async () => {
        const wrapper = mountEditor()
        const setupState = (wrapper.vm as any).$?.setupState as {
            saveListSelection: () => void
            applyBulletList: () => void
            applyOrderedList: () => void
            onContentAreaClick: (event: MouseEvent & { currentTarget: HTMLElement }) => void
        }

        // saveListSelection -> collapsed selection branch
        mocks.editor.state.selection = { from: 3, to: 3 }
        setupState.saveListSelection()
        setupState.applyBulletList()
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        // save/apply ordered list with range selection branch
        mocks.editor.state.selection = { from: 1, to: 4 }
        setupState.saveListSelection()
        setupState.applyOrderedList()
        expect(mocks.chain.setTextSelection).toHaveBeenCalledWith({ from: 1, to: 4 })
        expect(mocks.chain.toggleOrderedList).toHaveBeenCalled()

        // click guard: target not contained in currentTarget
        const fakeCurrentTarget = document.createElement('div')
        const externalTarget = document.createElement('span')
        const guardEvent = {
            currentTarget: fakeCurrentTarget,
            target: externalTarget,
            clientX: 0,
            clientY: 0,
            preventDefault: vi.fn(),
        } as unknown as MouseEvent & { currentTarget: HTMLElement }
        setupState.onContentAreaClick(guardEvent)
        expect(mocks.editor.commands.setTextSelection).not.toHaveBeenCalledWith(expect.any(Number))
    })

    it('covers null-editor guard branches for list helpers', () => {
        const wrapper = mountEditor()
        const setupState = (wrapper.vm as any).$?.setupState as {
            saveListSelection: () => void
            applyBulletList: () => void
            applyOrderedList: () => void
        }
        mocks.editorRef.value = null as any

        setupState.saveListSelection()
        setupState.applyBulletList()
        setupState.applyOrderedList()

        expect(mocks.chain.toggleBulletList).not.toHaveBeenCalled()
        expect(mocks.chain.toggleOrderedList).not.toHaveBeenCalled()
    })

    it('handles editor-null watch/click guards safely', async () => {
        mocks.editorRef.value = null as any
        const wrapper = mountEditor('')
        await wrapper.setProps({ modelValue: '<p>next-value</p>' })

        expect(mocks.editor.commands.setContent).not.toHaveBeenCalled()
        expect(wrapper.find('.tiptap-toolbar').exists()).toBe(false)

        const event = new MouseEvent('mousedown', {
            bubbles: true,
            cancelable: true,
            clientX: 5,
            clientY: 5,
        })
        wrapper.get('.tiptap-content').element.dispatchEvent(event)
        await nextTick()
        expect(mocks.editor.commands.setTextSelection).not.toHaveBeenCalled()
    })

    it('hides link remove button when current selection is not a link and uses highlight default color fallback', async () => {
        mocks.editor.isActive.mockImplementation((name: unknown) => name === 'bold')
        mocks.editor.getAttributes.mockImplementation((name: string): any => {
            if (name === 'textStyle') return { color: '', fontSize: '', lineHeight: '' }
            if (name === 'highlight') return { color: '' }
            if (name === 'link') return undefined
            return {}
        })

        const wrapper = mountEditor()
        await wrapper.get('button[title="Link"]').trigger('click')

        expect(wrapper.find('.link-popover-remove').exists()).toBe(false)
        expect((wrapper.get('.tiptap-color-input').element as HTMLInputElement).value).toBe('#fef08a')
    })

    it('exposes helpers for video/emoticon and destroys editor on unmount', () => {
        const wrapper = mountEditor()
        const vm = wrapper.vm as unknown as {
            setVideo: (src: string) => void
            setEmoticon: (image: { imageUrl: string }) => void
            fileIds: { value: number[] }
        }

        vm.setVideo('https://cdn.test/video')
        expect(mocks.chain.setVideo).toHaveBeenCalledWith({ src: 'https://cdn.test/video' })

        vm.setEmoticon({ imageUrl: 'https://cdn.test/e.png' })
        expect(mocks.chain.setImage).toHaveBeenCalledWith({
            src: 'https://cdn.test/e.png',
            alt: ':emoticon:',
            title: ':emoticon:',
        })

        wrapper.unmount()
        expect(mocks.editor.destroy).toHaveBeenCalled()
    })

    it('emits video and emoticon open events from toolbar buttons', async () => {
        const wrapper = mountEditor()

        await wrapper.get('button[title="Video"]').trigger('click')
        await wrapper.get('button[title="Emoticon"]').trigger('click')

        expect(wrapper.emitted('open-video')).toHaveLength(1)
        expect(wrapper.emitted('open-emoticon')).toHaveLength(1)
    })
})
