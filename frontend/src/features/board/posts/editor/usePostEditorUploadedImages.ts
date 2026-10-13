import { onScopeDispose, ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { closeHistory } from '@tiptap/pm/history'
import { escapeHtmlAttr } from '@/components/board/editor/postEditorHtml'
import { moveFromSelectedRawHtmlBlock } from '@/extensions/tiptap-raw-html-block'

export type UploadedEditorImage = { url: string; fileId?: number }

export function usePostEditorUploadedImages(
  editor: Ref<Editor | undefined>,
  emitFileUploaded: (fileId: number) => void,
) {
  const fileIds = ref<number[]>([])
  const editorImagePreviewUrls = new Set<string>()

  const insertUploadedImage = (uploaded: UploadedEditorImage, file: File) => {
    if (typeof uploaded.fileId === 'number') {
      fileIds.value.push(uploaded.fileId)
      emitFileUploaded(uploaded.fileId)
    }

    const previewUrl = URL.createObjectURL(file)
    editorImagePreviewUrls.add(previewUrl)
    const serverAttributes = [
      typeof uploaded.fileId === 'number' ? `data-file-id="${uploaded.fileId}"` : '',
      `data-server-src="${escapeHtmlAttr(uploaded.url)}"`,
    ].filter(Boolean).join(' ')
    if (editor.value) moveFromSelectedRawHtmlBlock(editor.value, 1)
    editor.value?.chain().focus().insertContent(`<img src="${escapeHtmlAttr(previewUrl)}" ${serverAttributes}>`).run()
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
    replaceUploadedImage,
    disposeUploadedImagePreviews,
  }
}
