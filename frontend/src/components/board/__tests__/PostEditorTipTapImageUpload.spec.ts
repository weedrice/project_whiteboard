import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { createDeferred } from '@/test/async'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import {
    cleanupPostEditorTipTapTestState,
    getPostEditorTipTapMocks,
    mountEditor,
    resetPostEditorTipTapTestState,
    selectors,
    setEditorDocument,
    setEditorSelection,
} from './PostEditorTipTapTestHarness'

const mocks = getPostEditorTipTapMocks()

function expectImageInserted(serverSrc: string, fileId: number, position: number = expect.any(Number)) {
    const attrs = { src: 'blob:https://noviis.kr/local-preview', serverSrc, fileId }
    expect(mocks.chain.insertContentAt).toHaveBeenCalledWith(position, { type: 'image', attrs }, { updateSelection: false })
    const images: unknown[] = []
    mocks.editor.state.doc.descendants(node => {
        if (node.type.name === 'image') images.push(node.attrs)
    })
    expect(images).toContainEqual(attrs)
}

describe('PostEditorTipTap image workflows', () => {
    afterEach(() => {
        cleanupPostEditorTipTapTestState()
    })

    beforeEach(() => {
        setActivePinia(createPinia())
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
        expectImageInserted('https://cdn.test/u1.png', 88, 1)
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
        expect(mocks.uploadImage).toHaveBeenCalledWith(firstFile, expect.objectContaining({
            expectedSession: { sessionGeneration: 0, userId: null },
            signal: expect.any(AbortSignal),
        }))
        expect(mocks.uploadImage).toHaveBeenCalledWith(secondFile, expect.objectContaining({
            expectedSession: { sessionGeneration: 0, userId: null },
            signal: expect.any(AbortSignal),
        }))
        expectImageInserted('https://cdn.test/first.png', 90)
        expectImageInserted('https://cdn.test/second.webp', 91)

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
        expectImageInserted('https://cdn.test/retry.png', 89)

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
        expect(mocks.uploadImage).toHaveBeenCalledWith(pastedFile, expect.objectContaining({
            expectedSession: { sessionGeneration: 0, userId: null },
            signal: expect.any(AbortSignal),
        }))
        expectImageInserted('https://cdn.test/pasted.png', 101)

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
        expect(mocks.uploadImage).toHaveBeenCalledWith(droppedFile, expect.objectContaining({
            expectedSession: { sessionGeneration: 0, userId: null },
            signal: expect.any(AbortSignal),
        }))
        expectImageInserted('https://cdn.test/dropped.webp', 102, 3)
    })

    it('applies the registered anchor plugin without replacing text selected during an upload', async () => {
        const pending = createDeferred<{ url: string; fileId: number }>()
        mocks.uploadImage.mockReturnValueOnce(pending.promise)
        const wrapper = mountEditor()
        setEditorDocument('start KEEP end')
        setEditorSelection({ from: 1, to: 1 })
        const fileInput = wrapper.get('input[type="file"]')
        Object.defineProperty(fileInput.element, 'files', {
            value: [new File(['image'], 'delayed.png', { type: 'image/png' })], configurable: true,
        })
        await fileInput.trigger('change')
        await flushPromises()
        expect(mocks.editor.registerPlugin).toHaveBeenCalledOnce()
        const plugin = mocks.editor.state.plugins[0]!
        expect(plugin.getState(mocks.editor.state).size).toBe(1)
        setEditorSelection({ from: 7, to: 11 })
        pending.resolve({ url: 'https://cdn.test/delayed.png', fileId: 103 })
        await flushPromises()

        expectImageInserted('https://cdn.test/delayed.png', 103, 1)
        expect(mocks.editor.state.doc.textContent).toBe('start KEEP end')
        const { from, to } = mocks.editor.state.selection
        expect(mocks.editor.state.doc.textBetween(from, to)).toBe('KEEP')
        expect(plugin.getState(mocks.editor.state).size).toBe(0)
        expect(mocks.chain.focus).not.toHaveBeenCalled()
        expect(wrapper.emitted('file-uploaded')).toEqual([[103]])
        wrapper.unmount()
        expect(mocks.editor.unregisterPlugin).toHaveBeenCalledOnce()
        expect(mocks.editor.state.plugins).toHaveLength(0)
    })

    it.each(['owner', 'session'] as const)('clears queued images when the %s changes while the editor remains mounted', async (boundary) => {
        const pending = createDeferred<{ url: string; fileId: number }>()
        mocks.uploadImage.mockReturnValueOnce(pending.promise)
        const wrapper = mountEditor()
        await wrapper.setProps({ uploadOwnerIdentity: 'first-draft' })
        const input = wrapper.get('input[type="file"]')
        const file = new File(['image'], 'old.png', { type: 'image/png' })
        Object.defineProperty(input.element, 'files', { value: [file, file], configurable: true })
        await input.trigger('change')
        await flushPromises()
        const config = mocks.uploadImage.mock.calls[0]?.[1]
        if (boundary === 'owner') await wrapper.setProps({ uploadOwnerIdentity: 'second-draft' })
        else useAuthStore().sessionGeneration += 1
        expect(config.signal.aborted).toBe(true)
        pending.resolve({ url: '/old-session.png', fileId: 12 })
        await flushPromises()
        expect(mocks.uploadImage).toHaveBeenCalledOnce()
        expect(mocks.chain.insertContentAt).not.toHaveBeenCalled()
        expect(wrapper.emitted('file-uploaded')).toBeUndefined()
        expect(wrapper.emitted('upload-pending')?.at(-1)).toEqual([false])
    })

    it('shows cancellable image upload progress while uploading', async () => {
        mocks.isUploadingImage.value = true
        const wrapper = mountEditor()

        expect(wrapper.find('.image-upload-status').text()).toContain('board.writePost.upload.uploading')
        await wrapper.find('.image-upload-status-btn').trigger('click')
        expect(mocks.abortImageUpload).toHaveBeenCalled()
    })

})
