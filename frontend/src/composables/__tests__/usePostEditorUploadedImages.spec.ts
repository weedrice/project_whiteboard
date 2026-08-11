import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, shallowRef } from 'vue'
import type { Editor } from '@tiptap/core'
import { usePostEditorUploadedImages } from '@/features/board/posts/editor/usePostEditorUploadedImages'

const createEditorRef = () => {
    const chain = {
        focus: vi.fn(),
        insertContent: vi.fn(),
        run: vi.fn(),
    }
    chain.focus.mockReturnValue(chain)
    chain.insertContent.mockReturnValue(chain)
    chain.run.mockReturnValue(true)

    const editor = {
        chain: vi.fn(() => chain),
        state: {
            selection: { from: 0, to: 0 },
        },
    }

    return {
        chain,
        editorRef: shallowRef<Editor | undefined>(editor as unknown as Editor),
    }
}

describe('usePostEditorUploadedImages', () => {
    beforeEach(() => {
        vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:https://noviis.kr/preview"unsafe')
        vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('tracks uploaded file ids and inserts escaped preview image html', () => {
        const { chain, editorRef } = createEditorRef()
        const emitFileUploaded = vi.fn()
        const { fileIds, insertUploadedImage } = usePostEditorUploadedImages(editorRef, emitFileUploaded)
        const file = new File(['image'], 'preview.png', { type: 'image/png' })

        insertUploadedImage({ url: 'https://cdn.test/image"unsafe.png', fileId: 42 }, file)

        expect(fileIds.value).toEqual([42])
        expect(emitFileUploaded).toHaveBeenCalledWith(42)
        expect(URL.createObjectURL).toHaveBeenCalledWith(file)
        expect(chain.insertContent).toHaveBeenCalledWith(
            '<img src="blob:https://noviis.kr/preview&quot;unsafe" data-file-id="42" data-server-src="https://cdn.test/image&quot;unsafe.png">',
        )
    })

    it('supports uploads without file ids and revokes all preview urls on dispose', () => {
        const { chain, editorRef } = createEditorRef()
        const emitFileUploaded = vi.fn()
        const { fileIds, insertUploadedImage, disposeUploadedImagePreviews } = usePostEditorUploadedImages(editorRef, emitFileUploaded)
        vi.mocked(URL.createObjectURL)
            .mockReturnValueOnce('blob:https://noviis.kr/one')
            .mockReturnValueOnce('blob:https://noviis.kr/two')

        insertUploadedImage({ url: 'https://cdn.test/no-id.png' }, new File(['one'], 'one.png', { type: 'image/png' }))
        insertUploadedImage({ url: 'https://cdn.test/with-id.png', fileId: 7 }, new File(['two'], 'two.png', { type: 'image/png' }))
        disposeUploadedImagePreviews()

        expect(fileIds.value).toEqual([7])
        expect(emitFileUploaded).toHaveBeenCalledTimes(1)
        expect(chain.insertContent.mock.calls[0][0]).not.toContain('data-file-id')
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://noviis.kr/one')
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://noviis.kr/two')
    })

    it('revokes preview urls when the owning effect scope is disposed', () => {
        const scope = effectScope()
        const { editorRef } = createEditorRef()

        scope.run(() => {
            const { insertUploadedImage } = usePostEditorUploadedImages(editorRef, vi.fn())
            insertUploadedImage({ url: 'https://cdn.test/scoped.png' }, new File(['scoped'], 'scoped.png', { type: 'image/png' }))
        })

        scope.stop()

        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://noviis.kr/preview"unsafe')
    })
})
