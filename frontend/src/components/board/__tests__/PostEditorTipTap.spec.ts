import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import {
    cleanupPostEditorTipTapTestState,
    getEditorDomEventHandler,
    getPostEditorTipTapMocks,
    mockNextEditorPositionAtCoords,
    mountEditor,
    resetPostEditorTipTapTestState,
    selectors,
    setEditorRefValue,
    setEditorSelection,
    triggerEditorUpdate,
} from './PostEditorTipTapTestHarness'

const mocks = getPostEditorTipTapMocks()
describe('PostEditorTipTap', () => {
    afterEach(() => {
        cleanupPostEditorTipTapTestState()
    })

    beforeEach(() => {
        resetPostEditorTipTapTestState()
    })

    it('emits updated html and syncs external model changes', async () => {
        const wrapper = mountEditor('<p>old</p>')

        triggerEditorUpdate()
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['<p>editor-html</p>'])

        await wrapper.setProps({ modelValue: '<p>remote</p>' })
        expect(mocks.editor.commands.setContent).toHaveBeenCalledWith('<p>remote</p>', { emitUpdate: false })

        mocks.editor.commands.setContent.mockClear()
        await wrapper.setProps({ modelValue: '<p>editor-html</p>' })
        expect(mocks.editor.commands.setContent).not.toHaveBeenCalled()
    })

    it('handles link click guards and slash key opening', async () => {
        const wrapper = mountEditor()
        const clickHandler = getEditorDomEventHandler<MouseEvent>('click')
        const keyHandler = getEditorDomEventHandler<KeyboardEvent>('keydown')

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

        setEditorSelection({
            from: 1,
            to: 1,
            $from: {
                parent: { textContent: '' },
                parentOffset: 0,
            },
        })
        const emptyParagraphSlashEvent = {
            key: '/',
            preventDefault: vi.fn(),
        } as unknown as KeyboardEvent
        expect(keyHandler(null, emptyParagraphSlashEvent)).toBe(true)
        expect(emptyParagraphSlashEvent.preventDefault).toHaveBeenCalled()
        await nextTick()
        expect(wrapper.find('.slash-popover').exists()).toBe(true)
    })

    it('applies toolbar formatting controls and table insertion', async () => {
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

        expect(wrapper.text()).toContain('Font size')
        expect(wrapper.text()).toContain('Line height')
        expect(wrapper.get('.tiptap-color-bar').attributes('style')).toContain('rgb(34, 197, 94)')

        const selects = wrapper.findAll('select.tiptap-select')
        await selects[0].setValue('18px')
        await selects[1].setValue('1.5')
        await selects[0].setValue('')
        await selects[1].setValue('')

        const colorInput = wrapper.get('.tiptap-color-input')
        expect(colorInput.attributes()).toMatchObject({
            name: 'editorToolbarTextColor',
            autocomplete: 'off',
        })
        await colorInput.setValue('#22c55e')
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
        expect(mocks.chain.setColor).toHaveBeenCalledWith('#22c55e')
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

        await wrapper.findAll('.link-popover-input')[0].setValue('javascript:alert(1)')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.toastAdd).toHaveBeenLastCalledWith('board.writePost.invalidLinkUrl', 'error')

        await wrapper.findAll('.link-popover-input')[0].setValue('example.com')
        await wrapper.findAll('.link-popover-input')[1].setValue('Example')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('href="https://example.com/"'))
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('>Example</a>'))

        mocks.chain.setLink.mockClear()
        mocks.editor.state.selection = { from: 2, to: 5 }
        await wrapper.get(selectors.link).trigger('click')
        await wrapper.findAll('.link-popover-input')[0].setValue('https://selected.test')
        await wrapper.findAll('.link-popover-actions button').at(-1)!.trigger('click')
        expect(mocks.chain.setLink).toHaveBeenCalledWith({ href: 'https://selected.test/' })

        mocks.editor.isActive.mockImplementation((name: unknown) => name === 'link')
        await wrapper.get(selectors.link).trigger('click')
        await wrapper.find('.link-popover-remove').trigger('click')
        expect(mocks.chain.extendMarkRange).toHaveBeenCalledWith('link')
        expect(mocks.chain.unsetLink).toHaveBeenCalled()

        mocks.editor.isActive.mockImplementation((name: unknown) => name === 'bold')
        await wrapper.get(selectors.link).trigger('click')
        expect(wrapper.find('.link-popover-remove').exists()).toBe(false)
    })

    it('keeps popover escape events inside the editor and exposes dialog accessibility attributes', async () => {
        const wrapper = mountEditor()
        const documentKeydown = vi.fn()
        document.addEventListener('keydown', documentKeydown)

        const dispatchEscape = (element: Element) => {
            element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
        }

        expect(wrapper.get(selectors.image).attributes('aria-label')).toBe('board.writePost.toolbar.image')
        expect(wrapper.get(selectors.video).attributes('aria-label')).toBe('board.writePost.toolbar.video')
        expect(wrapper.findAll('select.tiptap-select')[0].attributes('aria-label')).toBe('board.writePost.fontSize')
        expect(wrapper.findAll('select.tiptap-select')[1].attributes('aria-label')).toBe('board.writePost.lineHeight')

        await wrapper.get(selectors.link).trigger('click')
        expect(wrapper.get('.link-popover').attributes('aria-modal')).toBe('true')
        dispatchEscape(wrapper.get('#editor-link-url').element)
        await nextTick()
        expect(wrapper.find('#editor-link-url').exists()).toBe(false)

        await wrapper.get(selectors.slashMenu).trigger('click')
        expect(wrapper.get('.slash-popover').attributes('aria-modal')).toBe('true')
        dispatchEscape(wrapper.get('.slash-popover').element)
        await nextTick()
        expect(wrapper.find('.slash-popover').exists()).toBe(false)

        await wrapper.get('.tiptap-color-trigger').trigger('click')
        expect(wrapper.get('.color-panel').attributes('aria-modal')).toBeUndefined()
        expect(wrapper.findAll('.color-panel-swatch')[0].attributes('aria-label')).toBe('board.writePost.colorLabels.black')
        dispatchEscape(wrapper.get('.color-panel').element)
        await nextTick()
        expect(wrapper.find('.color-panel').exists()).toBe(false)

        await wrapper.get(selectors.tableDialog).trigger('click')
        expect(wrapper.get('.table-popover').attributes('aria-modal')).toBe('true')
        dispatchEscape(wrapper.get('#editor-table-rows').element)
        await nextTick()
        expect(wrapper.find('.table-popover').exists()).toBe(false)

        expect(wrapper.find('button[title="board.writePost.toolbar.more"]').exists()).toBe(false)
        expect(documentKeydown).not.toHaveBeenCalled()
        document.removeEventListener('keydown', documentKeydown)
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
        expect(wrapper.findAll('.slash-action-btn')[0].attributes('tabindex')).toBe('0')
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'ArrowDown' })
        await nextTick()
        expect(wrapper.findAll('.slash-action-btn')[1].attributes('tabindex')).toBe('0')
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'ArrowUp' })
        await nextTick()
        expect(wrapper.findAll('.slash-action-btn')[0].attributes('tabindex')).toBe('0')
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'End' })
        await nextTick()
        expect(wrapper.findAll('.slash-action-btn')[6].attributes('tabindex')).toBe('0')
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'Home' })
        await nextTick()
        expect(wrapper.findAll('.slash-action-btn')[0].attributes('tabindex')).toBe('0')
        await wrapper.findAll('.slash-action-btn')[0].trigger('click')
        expect(mocks.chain.toggleHeading).toHaveBeenCalledWith({ level: 2 })

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'ArrowDown' })
        await wrapper.get('[role="menu"]').trigger('keydown', { key: 'Enter' })
        expect(mocks.chain.setBlockquote).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[2].trigger('click')
        expect(mocks.chain.toggleBulletList).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[3].trigger('click')
        expect(wrapper.find('.link-popover').exists()).toBe(true)

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[4].trigger('click')
        expect(wrapper.find('.table-popover').exists()).toBe(true)

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[5].trigger('click')
        expect(mocks.chain.toggleCodeBlock).toHaveBeenCalled()

        await wrapper.get(selectors.slashMenu).trigger('click')
        await wrapper.findAll('.slash-action-btn')[6].trigger('click')
        expect(mocks.chain.setHorizontalRule).toHaveBeenCalled()

        expect(imageInputClickSpy).not.toHaveBeenCalled()
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

        mockNextEditorPositionAtCoords(null)
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

        setEditorRefValue(null)
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

    it('renders grouped desktop toolbar controls with insert block affordance', () => {
        const wrapper = mountEditor()
        const toolbarRows = wrapper.findAll('.tiptap-toolbar-row')
        const firstRow = toolbarRows[0]
        const secondRow = toolbarRows[1]

        expect(toolbarRows).toHaveLength(2)
        expect(firstRow.find('button[title="board.writePost.toolbar.bold"]').exists()).toBe(true)
        expect(firstRow.find('button[title="board.writePost.toolbar.link"]').exists()).toBe(false)
        expect(firstRow.find(selectors.bulletList).exists()).toBe(true)
        expect(firstRow.find(selectors.orderedList).exists()).toBe(true)
        expect(secondRow.find(selectors.link).exists()).toBe(true)
        expect(secondRow.find(selectors.image).exists()).toBe(true)
        expect(secondRow.find(selectors.video).exists()).toBe(true)
        expect(secondRow.find(selectors.emoticon).exists()).toBe(true)
        expect(secondRow.find(selectors.tableDialog).exists()).toBe(true)
        expect(secondRow.find(selectors.divider).exists()).toBe(true)
        expect(wrapper.get(selectors.slashMenu).text()).toContain('board.writePost.toolbar.insertBlock')
        expect(wrapper.find('button[title="board.writePost.toolbar.more"]').exists()).toBe(false)
    })
})
