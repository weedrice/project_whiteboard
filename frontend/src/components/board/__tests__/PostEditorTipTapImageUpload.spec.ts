import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import {
    cleanupPostEditorTipTapTestState,
    getEditorHandleClickOn,
    getPostEditorTipTapMocks,
    mountEditor,
    resetPostEditorTipTapTestState,
    selectors,
} from './PostEditorTipTapTestHarness'

const mocks = getPostEditorTipTapMocks()

describe('PostEditorTipTap image workflows', () => {
    afterEach(() => {
        cleanupPostEditorTipTapTestState()
    })

    beforeEach(() => {
        resetPostEditorTipTapTestState()
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
        setFile('bad.svg', 'image/svg+xml')
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.badRequest', 'warning')

        mocks.validateImageFile.mockReturnValueOnce('size')
        setFile('big.png')
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.fileSizeExceeded', 'warning')

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockResolvedValueOnce({ url: 'https://cdn.test/u1.png', fileId: 88 })
        setFile('ok.png')
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('src="blob:https://noviis.kr/local-preview"'))
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-file-id="88"'))
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/u1.png"'))
        expect(wrapper.find('.image-alt-popover').exists()).toBe(false)

        const firstFile = new File(['a'], 'first.png', { type: 'image/png' })
        const secondFile = new File(['b'], 'second.webp', { type: 'image/webp' })
        Object.defineProperty(fileInput.element, 'files', {
            value: [firstFile, secondFile],
            configurable: true,
        })
        mocks.uploadImage
            .mockResolvedValueOnce({ url: 'https://cdn.test/first.png', fileId: 90 })
            .mockResolvedValueOnce({ url: 'https://cdn.test/second.webp', fileId: 91 })
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.uploadImage).toHaveBeenCalledWith(firstFile)
        expect(mocks.uploadImage).toHaveBeenCalledWith(secondFile)
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/first.png"'))
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/second.webp"'))

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockRejectedValueOnce(new Error('abort'))
        mocks.isAbortUploadError.mockReturnValueOnce(true)
        setFile('abort.png')
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.loggerError).not.toHaveBeenCalledWith('Image upload failed:', expect.anything())

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockRejectedValueOnce(new Error('failed'))
        mocks.isAbortUploadError.mockReturnValueOnce(false)
        setFile('err.png')
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.loggerError).toHaveBeenCalledWith('Image upload failed:', expect.any(Error))
        expect(mocks.toastAdd).toHaveBeenCalledWith('common.messages.uploadFailed', 'error')
        expect(wrapper.find('.image-upload-status').text()).toContain('common.messages.uploadFailed')
        expect(wrapper.get('.image-upload-status').attributes('role')).toBe('alert')
        expect(wrapper.get('.image-upload-status').attributes('aria-live')).toBe('assertive')
        expect(wrapper.get('.image-upload-status-btn--icon i').attributes('aria-hidden')).toBe('true')

        mocks.validateImageFile.mockReturnValueOnce(null)
        mocks.uploadImage.mockResolvedValueOnce({ url: 'https://cdn.test/retry.png', fileId: 89 })
        await wrapper.findAll('.image-upload-status-btn')[0].trigger('click')
        await flushPromises()
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/retry.png"'))

        wrapper.unmount()
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://noviis.kr/local-preview')
    })

    it('uploads pasted and dropped image files through the sequential queue', async () => {
        const wrapper = mountEditor()
        const contentArea = wrapper.get('.tiptap-content')
        const pastedFile = new File(['p'], 'pasted.png', { type: 'image/png' })
        const droppedFile = new File(['d'], 'dropped.webp', { type: 'image/webp' })

        mocks.uploadImage
            .mockResolvedValueOnce({ url: 'https://cdn.test/pasted.png', fileId: 101 })
            .mockResolvedValueOnce({ url: 'https://cdn.test/dropped.webp', fileId: 102 })

        const pasteEvent = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent
        Object.defineProperty(pasteEvent, 'clipboardData', {
            value: { files: [pastedFile] },
        })
        contentArea.element.dispatchEvent(pasteEvent)
        await flushPromises()
        expect(pasteEvent.defaultPrevented).toBe(true)
        expect(mocks.uploadImage).toHaveBeenCalledWith(pastedFile)
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/pasted.png"'))

        const dragEnterEvent = new Event('dragenter', { bubbles: true, cancelable: true }) as DragEvent
        Object.defineProperty(dragEnterEvent, 'dataTransfer', {
            value: { items: [{ kind: 'file', getAsFile: () => droppedFile }] },
        })
        contentArea.element.dispatchEvent(dragEnterEvent)
        await nextTick()
        expect(wrapper.find('.image-drop-overlay').text()).toContain('board.writePost.dropImageHint')

        const dropEvent = new Event('drop', { bubbles: true, cancelable: true }) as DragEvent
        Object.defineProperty(dropEvent, 'dataTransfer', {
            value: { files: [droppedFile] },
        })
        contentArea.element.dispatchEvent(dropEvent)
        await flushPromises()
        expect(dropEvent.defaultPrevented).toBe(true)
        expect(wrapper.find('.image-drop-overlay').exists()).toBe(false)
        expect(mocks.uploadImage).toHaveBeenCalledWith(droppedFile)
        expect(mocks.chain.insertContent).toHaveBeenCalledWith(expect.stringContaining('data-server-src="https://cdn.test/dropped.webp"'))
    })

    it('opens image alt editor from selected images and applies or clears alt text', async () => {
        const wrapper = mountEditor()
        const handleClickOn = getEditorHandleClickOn()
        const image = document.createElement('img')
        image.src = 'https://cdn.test/image.png'

        handleClickOn(null, 7, { type: { name: 'image' }, attrs: { alt: 'Original alt' } }, 7, {
            target: image,
        } as unknown as MouseEvent)
        await nextTick()

        expect(wrapper.find('.image-alt-popover').exists()).toBe(true)
        expect((wrapper.get('#editor-image-alt').element as HTMLInputElement).value).toBe('Original alt')
        expect(mocks.editor.commands.setTextSelection).toHaveBeenCalledWith(7)

        wrapper.findComponent({ name: 'PostEditorImageAltPopover' }).vm.$emit('apply', 'Updated alt')
        await nextTick()

        expect(mocks.chain.updateAttributes).toHaveBeenCalledWith('image', { alt: 'Updated alt' })
        expect(wrapper.find('.image-alt-popover').exists()).toBe(false)

        handleClickOn(null, 8, { type: { name: 'image' }, attrs: { alt: 'Clear me' } }, 8, {
            target: image,
        } as unknown as MouseEvent)
        await nextTick()
        wrapper.findComponent({ name: 'PostEditorImageAltPopover' }).vm.$emit('clear')
        await nextTick()

        expect(mocks.chain.updateAttributes).toHaveBeenCalledWith('image', { alt: null })
    })

    it('shows cancellable image upload progress while uploading', async () => {
        mocks.isUploadingImage.value = true
        const wrapper = mountEditor()

        expect(wrapper.find('.image-upload-status').text()).toContain('board.writePost.upload.uploading')
        await wrapper.find('.image-upload-status-btn').trigger('click')
        expect(mocks.abortImageUpload).toHaveBeenCalled()
    })

})
