import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, shallowRef } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { Editor } from '@tiptap/core'
import { Plugin } from '@tiptap/pm/state'
import { encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { createPostEditorExtensions } from '@/components/board/editor/postEditorExtensions'
import { usePostEditorUploadedImages, type UploadedEditorImage } from '@/features/board/posts/editor/usePostEditorUploadedImages'
import { useEditorImageUploadQueue } from '@/composables/useEditorImageUploadQueue'
import { createDeferred } from '@/test/async'

const file = () => new File(['image'], 'preview.png', { type: 'image/png' })

describe('post editor uploads with transaction-mapped anchors', () => {
  let editor: Editor
  let editorRef: ReturnType<typeof shallowRef<Editor | undefined>>
  let scope: ReturnType<typeof effectScope>
  let images: ReturnType<typeof usePostEditorUploadedImages>
  let queue: ReturnType<typeof useEditorImageUploadQueue<UploadedEditorImage>>
  let requests: ReturnType<typeof createDeferred<UploadedEditorImage>>[]
  const emitFileUploaded = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(URL, 'createObjectURL').mockImplementation(blob => `blob:preview-${(blob as File).name}`)
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    editor = new Editor({ extensions: createPostEditorExtensions(), content: '<p>start KEEP end</p>' })
    editorRef = shallowRef(editor)
    requests = []
    scope = effectScope()
    scope.run(() => {
      images = usePostEditorUploadedImages(editorRef, emitFileUploaded)
      queue = useEditorImageUploadQueue<UploadedEditorImage>({
        validate: () => null,
        upload: () => {
          const request = createDeferred<UploadedEditorImage>()
          requests.push(request)
          return request.promise
        },
        isAbort: () => false,
        onUploaded: images.insertUploadedImage,
        onItemReleased: images.releaseUploadAnchor,
      })
    })
  })

  afterEach(() => {
    queue.dispose()
    scope.stop()
    editorRef.value?.destroy()
    if (!editor.isDestroyed) editor.destroy()
    vi.restoreAllMocks()
  })

  function enqueue(files = [file()], dropEvent?: DragEvent) {
    images.enqueueImageFiles(queue, files, dropEvent)
  }
  async function finish(index = 0, fileId = index + 1) {
    requests[index]!.resolve({ url: `/api/v1/files/${fileId}`, fileId })
    await flushPromises()
  }
  function imageIds() {
    const ids: number[] = []
    editor.state.doc.descendants(node => { if (node.type.name === 'image') ids.push(node.attrs.fileId) })
    return ids
  }

  it('keeps text selected during a pending upload and inserts at the original caret without taking focus', async () => {
    editor.commands.setTextSelection(1)
    enqueue()
    editor.commands.setTextSelection({ from: 7, to: 11 })
    const input = document.body.appendChild(document.createElement('input'))
    input.focus()
    await finish()

    expect(editor.getText()).toBe('start KEEP end')
    expect(editor.state.doc.firstChild?.firstChild?.type.name).toBe('image')
    const { from, to } = editor.state.selection
    expect(editor.state.doc.textBetween(from, to)).toBe('KEEP')
    expect(document.activeElement).toBe(input)
    expect(images.fileIds.value).toEqual([1])
    input.remove()
  })

  it('maps the anchor through edits before it and gives image insertion its own undo step', async () => {
    editor.commands.setTextSelection(7)
    enqueue()
    editor.commands.insertContentAt(1, 'NEW ')
    await finish()

    expect(editor.getText()).toBe('NEW start KEEP end')
    expect(editor.getHTML()).toMatch(/NEW start <img[^>]*>KEEP end/)
    editor.commands.undo()
    expect(editor.getText()).toBe('NEW start KEEP end')
    expect(imageIds()).toEqual([])
    editor.commands.undo()
    expect(editor.getText()).toBe('start KEEP end')
  })

  it('uses the drop coordinates instead of the current selection', async () => {
    editor.commands.setTextSelection({ from: 1, to: 6 })
    const coords = vi.spyOn(editor.view, 'posAtCoords').mockReturnValue({ pos: 7, inside: 0 })
    enqueue([file()], { clientX: 120, clientY: 240 } as DragEvent)
    await finish()

    expect(coords).toHaveBeenCalledWith({ left: 120, top: 240 })
    expect(editor.getHTML()).toMatch(/start <img[^>]*>KEEP end/)
    expect(editor.getText()).toBe('start KEEP end')
  })

  it('keeps batch order when an earlier failed file is retried after a later image was inserted', async () => {
    const sameFile = file()
    editor.commands.setTextSelection(1)
    enqueue([sameFile, sameFile])
    requests[0]!.reject(new Error('offline'))
    await flushPromises()
    const failedId = queue.failedItems.value[0]!.id
    await finish(1, 2)
    editor.commands.setTextSelection({ from: 8, to: 12 })
    expect(queue.retryItem(failedId)).toBe(true)
    await finish(2, 1)

    expect(imageIds()).toEqual([1, 2])
    expect(editor.getText()).toBe('start KEEP end')
    const { from, to } = editor.state.selection
    expect(editor.state.doc.textBetween(from, to)).toBe('KEEP')
  })

  it.each(['undo', 'undo-and-redo', 'delete-later-image'] as const)('retains the earlier failed upload across %s of a later image', async operation => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file()])
    requests[0]!.reject(new Error('offline'))
    await flushPromises()
    const failedId = queue.failedItems.value[0]!.id
    await finish(1, 2)
    if (operation === 'delete-later-image') editor.commands.deleteRange({ from: 1, to: 2 })
    else {
      editor.commands.undo()
      if (operation === 'undo-and-redo') editor.commands.redo()
    }
    expect(queue.retryItem(failedId)).toBe(true)
    await finish(2, 1)

    expect(imageIds()).toEqual(operation === 'undo-and-redo' ? [1, 2] : [1])
    expect(editor.getText()).toBe('start KEEP end')
  })

  it('keeps an earlier failed upload before boundary typing after undo and redo', async () => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file()])
    requests[0]!.reject(new Error('offline'))
    await flushPromises()
    const failedId = queue.failedItems.value[0]!.id
    await finish(1, 2)
    editor.commands.undo()
    editor.commands.redo()
    editor.commands.insertContentAt(1, 'NEW ')
    expect(queue.retryItem(failedId)).toBe(true)
    await finish(2, 1)

    expect(imageIds()).toEqual([1, 2])
    expect(editor.getText()).toBe('NEW start KEEP end')
    const paragraph = editor.state.doc.firstChild!
    expect(paragraph.child(0).attrs.fileId).toBe(1)
    expect(paragraph.child(1).text).toBe('NEW ')
    expect(paragraph.child(2).attrs.fileId).toBe(2)
  })

  it('keeps a new anchor after text typed exactly at its insertion point', async () => {
    editor.commands.setTextSelection(1)
    enqueue()
    editor.commands.insertContentAt(1, 'NEW ')
    await finish()

    const paragraph = editor.state.doc.firstChild!
    expect(paragraph.child(0).text).toBe('NEW ')
    expect(paragraph.child(1).attrs.fileId).toBe(1)
    expect(editor.getText()).toBe('NEW start KEEP end')
  })

  it('still discards a left-affinity anchor when its entire document is replaced', async () => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file()])
    requests[0]!.reject(new Error('offline'))
    await flushPromises()
    const failedId = queue.failedItems.value[0]!.id
    await finish(1, 2)
    editor.commands.setContent('<p>another document</p>')
    expect(queue.retryItem(failedId)).toBe(true)
    await finish(2, 1)

    expect(imageIds()).toEqual([])
    expect(editor.getText()).toBe('another document')
  })

  it.each([[1, 2], [2, 1]])('keeps three-file order when two failures are retried in order %s then %s', async (firstId, secondId) => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file(), file()])
    requests[0]!.reject(new Error('first offline'))
    await flushPromises()
    requests[1]!.reject(new Error('second offline'))
    await flushPromises()
    await finish(2, 3)
    editor.commands.undo()
    editor.commands.redo()
    expect(queue.retryItem(firstId)).toBe(true)
    await finish(3, firstId)
    expect(queue.retryItem(secondId)).toBe(true)
    await finish(4, secondId)

    expect(imageIds()).toEqual([1, 2, 3])
    expect(editor.getText()).toBe('start KEEP end')
  })

  it.each(['start', 'end'] as const)('discards a drop anchor at the document %s when the whole document is replaced', async boundary => {
    const position = boundary === 'start' ? 0 : editor.state.doc.content.size
    vi.spyOn(editor.view, 'posAtCoords').mockReturnValue({ pos: position, inside: -1 })
    enqueue([file()], { clientX: 120, clientY: 240 } as DragEvent)
    editor.commands.setContent('<p>another document</p>')
    await finish()

    expect(imageIds()).toEqual([])
    expect(editor.getText()).toBe('another document')
  })

  it('recognizes whole-document replacement after an earlier step in the same transaction', async () => {
    vi.spyOn(editor.view, 'posAtCoords').mockReturnValue({ pos: 0, inside: -1 })
    enqueue([file()], { clientX: 120, clientY: 240 } as DragEvent)
    const transaction = editor.state.tr.insertText('NEW ', 1)
    transaction.replaceWith(0, transaction.doc.content.size,
      editor.schema.nodes.paragraph!.create(null, editor.schema.text('another document')))
    editor.view.dispatch(transaction)
    await finish()

    expect(imageIds()).toEqual([])
    expect(editor.getText()).toBe('another document')
  })

  it('maps every queued file while earlier uploads complete and the user continues typing', async () => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file(), file()])
    await finish(0, 1)
    editor.commands.insertContentAt(2, 'NEW ')
    await finish(1, 2)
    await finish(2, 3)

    expect(imageIds()).toEqual([1, 2, 3])
    expect(editor.getText()).toBe('NEW start KEEP end')
  })

  it.each(['delete', 'replace-document'] as const)('discards an anchor removed by %s without inserting at the new caret', async operation => {
    editor.commands.setTextSelection(7)
    enqueue()
    if (operation === 'delete') editor.commands.deleteRange({ from: 1, to: 11 })
    else editor.commands.setContent('<p>another document</p>')
    const remainingText = editor.getText()
    await finish()

    expect(editor.getText()).toBe(remainingText)
    expect(imageIds()).toEqual([])
    // The form still owns the unused upload and can discard it on teardown.
    expect(emitFileUploaded).toHaveBeenCalledWith(1)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it.each(['cancel', 'clear', 'dispose'] as const)('does not insert canceled uploads after %s', async action => {
    editor.commands.setTextSelection(1)
    enqueue([file(), file()])
    queue[action]()
    await finish()

    expect(imageIds()).toEqual([])
    expect(requests).toHaveLength(1)
    expect(editor.getText()).toBe('start KEEP end')
    expect(images.insertUploadedImage({ url: '/late.png' }, file(), 1)).toBe(false)
  })

  it('removes failed anchors when dismissed', async () => {
    enqueue()
    requests[0]!.reject(new Error('offline'))
    await flushPromises()
    const failedId = queue.failedItems.value[0]!.id
    queue.dismissItem(failedId)

    expect(images.insertUploadedImage({ url: '/late.png' }, file(), failedId)).toBe(false)
    expect(imageIds()).toEqual([])
  })

  it('drops anchors when the editor instance changes', async () => {
    enqueue()
    editorRef.value = new Editor({ extensions: createPostEditorExtensions(), content: '<p>replacement</p>' })
    await finish()

    expect(editor.getText()).toBe('start KEEP end')
    expect(editorRef.value.getText()).toBe('replacement')
    expect(editorRef.value.getHTML()).not.toContain('<img')
  })

  it('ignores completion after editor destruction', async () => {
    enqueue()
    editor.destroy()
    await finish()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('serializes unsafe-looking URL attributes safely and releases preview URLs on scope disposal', async () => {
    vi.mocked(URL.createObjectURL).mockReturnValue('blob:preview"unsafe')
    enqueue()
    requests[0]!.resolve({ url: 'https://cdn.test/image"unsafe.png', fileId: 42 })
    await flushPromises()

    expect(images.fileIds.value).toEqual([42])
    expect(editor.getHTML()).toContain('data-server-src="https://cdn.test/image&quot;unsafe.png"')
    expect(editor.state.doc.firstChild?.firstChild?.attrs.src).toBe('blob:preview"unsafe')
    scope.stop()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview"unsafe')
  })

  it('inserts after the selected raw HTML block without changing a later node selection', async () => {
    editor.commands.setContent(encodeSandboxedPostHtml('<div>Raw HTML</div>') + '<p>KEEP</p>')
    editor.commands.setNodeSelection(0)
    enqueue()
    editor.commands.setNodeSelection(0)
    await finish()
    expect(editor.state.doc.firstChild?.type.name).toBe('rawHtmlBlock')
    expect(editor.state.doc.child(1).firstChild?.type.name).toBe('image')
    expect(editor.state.doc.textContent).toContain('KEEP')
    expect(editor.state.selection.from).toBe(0)
    expect(editor.state.selection.to).toBe(1)
  })

  it('does not insert when the editor becomes read-only while uploading', async () => {
    enqueue()
    editor.setEditable(false)
    await finish()
    expect(editor.getText()).toBe('start KEEP end')
    expect(imageIds()).toEqual([])
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('releases the preview if a plugin rejects the insertion transaction', async () => {
    enqueue()
    editor.registerPlugin(new Plugin({ filterTransaction: transaction => !transaction.docChanged }))
    await finish()
    expect(imageIds()).toEqual([])
    expect(editor.getText()).toBe('start KEEP end')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-preview.png')
    expect(images.insertUploadedImage({ url: '/late.png' }, file(), 1)).toBe(false)
  })

  it('supports a successful upload without a file id', async () => {
    enqueue()
    requests[0]!.resolve({ url: '/no-id.png' })
    await flushPromises()
    expect(editor.getHTML()).toContain('data-server-src="/no-id.png"')
    expect(images.fileIds.value).toEqual([])
    expect(emitFileUploaded).not.toHaveBeenCalled()
  })

})
