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
        toggleBlockquote: vi.fn(),
        setHorizontalRule: vi.fn(),
        setImage: vi.fn(),
        setVideo: vi.fn(),
        run: vi.fn(),
    }
    Object.values(chain).forEach((method) => {
        method.mockImplementation(() => chain)
    })
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
    const icon = defineComponent({ name: 'TestIcon', setup: () => () => h('i') })
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

const selectors = {
    more: 'button[title="board.writePost.toolbar.more"]',
    link: 'button[title="board.writePost.toolbar.link"]',
    image: 'button[title="board.writePost.toolbar.image"]',
    video: 'button[title="board.writePost.toolbar.video"]',
    bulletList: 'button[title="board.writePost.toolbar.bulletList"]',
    orderedList: 'button[title="board.writePost.toolbar.orderedList"]',
    emoticon: 'button[title="board.writePost.toolbar.emoticon"]',
    slashMenu: 'button[title="board.writePost.toolbar.slashMenu"]',
    divider: 'button[title="board.writePost.toolbar.divider"]',
    tableDialog: 'button[title="board.writePost.toolbar.tableDialog"]',
} as const

const openAdvancedTools = async (wrapper: ReturnType<typeof mountEditor>) => {
    await wrapper.get(selectors.more).trigger('click')
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

        mocks.editor.commands.setContent.mockClear()
        await wrapper.setProps({ modelValue: '<p>editor-html</p>' })
        expect(mocks.editor.commands.setContent).not.toHaveBeenCalled()
    })

    it('handles link click guards and slash key opening', () => {
        const wrapper = mountEditor()
        const clickHandler = (mocks.editorOptions.value?.editorProps as any).handleDOMEvents.click
        const keyHandler = (mocks.editorOptions.value?.editorProps as any).handleDOMEvents.keydown

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

        const slashEvent = { key: '/' } as KeyboardEvent
        expect(keyHandler(null, slashEvent)).toBe(false)
        expect(wrapper.find('.slash-popover').exists()).toBe(false)
    })

    it('applies advanced formatting controls and table insertion', async () => {
        mocks.i18nT.mockImplementation((key: string) => {
            if (key === 'board.writePost.fontSize') return ''
            if (key === 'board.writePost.lineHeight') return ''
            return key
        })
        mocks.editor.getAttributes.mockImplementation((name: string): any => {
            if (name === 'textStyle') return { color: '#22c55e', fontSize: '', lineHeight: '' }
            if (name === 'highlight') return { color: '' }
            return {}
        })

        const wrapper = mountEditor()
        await openAdvancedTools(wrapper)

        expect(wrapper.text()).toContain('Font size')
        expect(wrapper.text()).toContain('Line height')
        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('rgb(34, 197, 94)')

        const selects = wrapper.findAll('select.tiptap-select')
        await selects[0].setValue('18px')
        await selects[1].setValue('1.5')
        await selects[0].setValue('')
        await selects[1].setValue('')

        await wrapper.get('.tiptap-color-input').setValue('#22c55e')
        await wrapper.get('.tiptap-color-trigger').trigger('click')
        await wrapper.findAll('.color-panel-swatch')[0].trigger('click')
        await wrapper.get('.tiptap-color-trigger').trigger('click')
        await wrapper.get('.color-panel-default').trigger('click')

        await wrapper.get('button[title="board.writePost.alignLeft"]').trigger('click')
        await wrapper.get('button[title="board.writePost.alignCenter"]').trigger('click')
        await wrapper.get('button[title="board.writePost.alignRight"]').trigger('click')
        await wrapper.get('button[title="board.writePost.alignJustify"]').trigger('click')
        await wrapper.get(selectors.divider).trigger('click')

        await wrapper.get(selectors.tableDialog).trigger('click')
        const numberInputs = wrapper.findAll('.table-popover .link-popover-input')
        await numberInputs[0].setValue('99')
        await numberInputs[1].setValue('0')
        await wrapper.get('#table-header-row').setValue(false)
        await wrapper.findAll('.table-popover .link-popover-actions button').at(-1)!.trigger('click')

        expect(mocks.chain.setFontSize).toHaveBeenCalledWith('18px')
        expect(mocks.chain.setLineHeight).toHaveBeenCalledWith('1.5')
        expect(mocks.chain.unsetFontSize).toHaveBeenCalled()
        expect(mocks.chain.unsetLineHeight).toHaveBeenCalled()
        expect(mocks.chain.setHighlight).toHaveBeenCalledWith({ color: '#22c55e' })
        expect(mocks.chain.setColor).toHaveBeenCalled()
        expect(mocks.chain.unsetColor).toHaveBeenCalled()
        expect(mocks.chain.insertTable).toHaveBeenCalledWith({ rows: 20, cols: 3, withHeaderRow: false })
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('left')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('center')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('right')
        expect(mocks.chain.setTextAlign).toHaveBeenCalledWith('justify')
        expect(mocks.chain.setHorizontalRule).toHaveBeenCalled()
    })

    it('handles link creation, validation and removal states', async () => {
        const wrapper = mountEditor()

        await wrapper.get(selectors.link).trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.toastAdd).toHaveBeenLastCalledWith('board.writePost.linkUrlPrompt', 'error')

        await wrapper.findAll('.link-popover-input')[0].setValue('example.com')
        await wrapper.findAll('.link-popover-input')[1].setValue('Example')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('https://example.com'))

        mocks.chain.setLink.mockClear()
        mocks.editor.state.selection = { from: 2, to: 5 }
        await wrapper.get(selectors.link).trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://selected.test')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.setLink).toHaveBeenCalledWith({ href: 'https://selected.test' })

        mocks.editor.isActive.mockImplementation((name: unknown) => name === 'link')
        await wrapper.get(selectors.link).trigger('click')
        await wrapper.find('.link-popover-remove').trigger('click')
        expect(mocks.chain.extendMarkRange).toHaveBeenCalledWith('link')
        expect(mocks.chain.unsetLink).toHaveBeenCalled()

        mocks.editor.isActive.mockImplementation((name: unknown) => name === 'bold')
        await openAdvancedTools(wrapper)
        await wrapper.get(selectors.link).trigger('click')
        expect(wrapper.find('.link-popover-remove').exists()).toBe(false)
    })

    it('handles image upload validation success and failure flows', async () => {
        const wrapper = mountEditor()
        const imageBtn = wrapper.get(selectors.image)
        const fileInput = wrapper.get('input[type="file"]')
        const clickSpy = vi.spyOn(fileInput.element as HTMLInputElement, 'click')
        await imageBtn.trigger('click')
        expect(clickSpy).toHaveBeenCalled()

        Object.defineProperty(fileInput.element, 'files', {
            value: [],
            configurable: true,
        })
        await fileInput.trigger('change')
        expect(mocks.validateImageFile).not.toHaveBeenCalled()

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

    it('supports list helpers and slash menu actions', async () => {
        const wrapper = mountEditor()
        const imageInputClickSpy = vi.spyOn(wrapper.get('input[type="file"]').element as HTMLInputElement, 'click')

        mocks.editor.state.selection = { from: 2, to: 6 }
        const bulletBtn = wrapper.get(selectors.bulletList)
        await bulletBtn.trigger('mousedown')
        await bulletBtn.trigger('click')
        expect(mocks.chain.setTextSelection).toHaveBeenCalledWith({ from: 2, to: 6 })
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        mocks.editor.state.selection = { from: 4, to: 8 }
        const orderedBtn = wrapper.get(selectors.orderedList)
        await orderedBtn.trigger('mousedown')
        await orderedBtn.trigger('click')
        expect(mocks.chain.setTextSelection).toHaveBeenCalledWith({ from: 4, to: 8 })
        expect(mocks.chain.toggleOrderedList).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        expect(wrapper.find('.slash-popover').exists()).toBe(true)
        await wrapper.findAll('.slash-action-btn')[0].trigger('click')
        expect(imageInputClickSpy).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[1].trigger('click')
        expect(mocks.chain.toggleBlockquote).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[2].trigger('click')
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[3].trigger('click')
        expect(wrapper.find('.link-popover').exists()).toBe(true)

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[4].trigger('click')
        expect(mocks.chain.setHorizontalRule).toHaveBeenCalled()
    })

    it('handles content area cursor placement and editor-null guards safely', async () => {
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

        mocks.editorRef.value = null as any
        const nullWrapper = mountEditor('')
        await nullWrapper.setProps({ modelValue: '<p>next-value</p>' })
        expect(mocks.editor.commands.setContent).not.toHaveBeenCalled()
        expect(nullWrapper.find('.tiptap-toolbar').exists()).toBe(false)
    })

    it('exposes helpers for video and emoticon and emits toolbar events', async () => {
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

        await wrapper.get(selectors.video).trigger('click')
        await wrapper.get(selectors.emoticon).trigger('click')
        expect(wrapper.emitted('open-video')).toHaveLength(1)
        expect(wrapper.emitted('open-emoticon')).toHaveLength(1)

        wrapper.unmount()
        expect(mocks.editor.destroy).toHaveBeenCalled()
    })
})
