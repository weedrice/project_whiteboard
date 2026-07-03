import { onScopeDispose, ref, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { escapeHtmlAttr } from '@/components/board/editor/postEditorHtml'

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
    editor.value?.chain().focus().insertContent(`<img src="${escapeHtmlAttr(previewUrl)}" ${serverAttributes}>`).run()
  }

  const disposeUploadedImagePreviews = () => {
    editorImagePreviewUrls.forEach((url) => URL.revokeObjectURL(url))
    editorImagePreviewUrls.clear()
  }

  onScopeDispose(disposeUploadedImagePreviews, true)

  return {
    fileIds,
    insertUploadedImage,
    disposeUploadedImagePreviews,
  }
}
