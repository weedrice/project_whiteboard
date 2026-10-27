import { onScopeDispose, ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { closeHistory } from '@tiptap/pm/history'
import { moveFromSelectedRawHtmlBlock } from '@/extensions/tiptap-raw-html-block'
import { usePostEditorUploadAnchors } from './usePostEditorUploadAnchors'

export type UploadedEditorImage = { url: string; fileId?: number }

export function usePostEditorUploadedImages(
  editor: Ref<Editor | undefined>,
  emitFileUploaded: (fileId: number) => void,
) {
  const fileIds = ref<number[]>([])
  const editorImagePreviewUrls = new Set<string>()
  const anchors = usePostEditorUploadAnchors(editor)

  function enqueueImageFiles(
    queue: { enqueueFiles: (files: File[]) => Array<{ id: number; status: string }> },
    files: File[],
    dropEvent?: DragEvent,
  ) {
    const instance = editor.value
    if (!instance || instance.isDestroyed || !instance.isEditable) return
    if (!dropEvent) moveFromSelectedRawHtmlBlock(instance, 1)
    const position = dropEvent
      ? instance.view.posAtCoords({ left: dropEvent.clientX, top: dropEvent.clientY })?.pos
      : instance.state.selection.from
    if (position == null) return
    // enqueue starts the upload, but its await cannot settle before this synchronous capture.
    const items = queue.enqueueFiles(files)
    anchors.add(items.filter(item => item.status !== 'canceled').map(item => item.id), position)
  }

  const insertUploadedImage = (uploaded: UploadedEditorImage, file: File, itemId: number) => {
    if (typeof uploaded.fileId === 'number') {
      fileIds.value.push(uploaded.fileId)
      emitFileUploaded(uploaded.fileId)
    }
    // Deleted anchors and replaced/destroyed editors must not redirect a late upload.
    if (!editor.value?.isEditable || !anchors.has(itemId)) return false
    const previewUrl = URL.createObjectURL(file)
    editorImagePreviewUrls.add(previewUrl)
    const inserted = anchors.insert(itemId, {
      type: 'image',
      attrs: { src: previewUrl, serverSrc: uploaded.url, fileId: uploaded.fileId ?? null },
    })
    if (!inserted) {
      URL.revokeObjectURL(previewUrl)
      editorImagePreviewUrls.delete(previewUrl)
    }
    return inserted
  }

  const disposeUploadedImagePreviews = () => {
    editorImagePreviewUrls.forEach((url) => URL.revokeObjectURL(url))
    editorImagePreviewUrls.clear()
  }

  const replaceUploadedImage = (uploaded: UploadedEditorImage, file: File, position: number): boolean => {
    const instance = editor.value
    const node = instance?.state.doc.nodeAt(position)
    if (!instance?.isEditable || node?.type.name !== 'image') return false
    const previewUrl = URL.createObjectURL(file)
    editorImagePreviewUrls.add(previewUrl)
    instance.view.dispatch(closeHistory(instance.state.tr).setNodeMarkup(position, undefined, {
      ...node.attrs, src: previewUrl, serverSrc: uploaded.url, fileId: uploaded.fileId ?? null,
    }))
    instance.view.dispatch(closeHistory(instance.state.tr))
    if (typeof uploaded.fileId === 'number') {
      fileIds.value.push(uploaded.fileId)
      emitFileUploaded(uploaded.fileId)
    }
    return true
  }

  onScopeDispose(disposeUploadedImagePreviews, true)

  return {
    fileIds,
    insertUploadedImage,
    enqueueImageFiles,
    releaseUploadAnchor: anchors.remove,
    replaceUploadedImage,
    disposeUploadedImagePreviews,
  }
}
