import { provide, ref, type InjectionKey, type Ref } from 'vue'
import type { Editor } from '@tiptap/core'
import { fileApi } from '@/api/file'
import logger from '@/utils/logger'
import { useEditorImageUpload } from './useEditorImageUpload'
import type { UploadedEditorImage } from './usePostEditorUploadedImages'

export const postEditorImageReplacementKey: InjectionKey<{
  busy: Ref<boolean>
  request: (getPosition: () => number | undefined) => void
}> = Symbol('postEditorImageReplacement')

export function usePostEditorImageReplacement(options: {
  editor: Ref<Editor | undefined>
  ownerIdentity: Readonly<Ref<string | undefined>>
  replace: (uploaded: UploadedEditorImage, file: File, position: number) => boolean
  invalid: (error: 'type' | 'size') => void
  failed: () => void
}) {
  const input = ref<HTMLInputElement | null>(null)
  const uploader = useEditorImageUpload(undefined, options.ownerIdentity)
  let target: { editor: Editor; getPosition: () => number | undefined; src: string } | null = null
  provide(postEditorImageReplacementKey, {
    busy: uploader.isUploadingImage,
    request(getPosition) {
      const editor = options.editor.value
      const position = getPosition()
      if (!editor?.isEditable || position == null || uploader.isUploadingImage.value) return
      const node = editor.state.doc.nodeAt(position)
      if (node?.type.name !== 'image') return
      target = { editor, getPosition, src: node.attrs.src }
      input.value?.click()
    },
  })
  async function onChange(event: Event) {
    const element = event.target as HTMLInputElement
    const file = element.files?.[0]
    element.value = ''
    const current = target
    target = null
    if (!file || !current || uploader.isUploadingImage.value) return
    const error = uploader.validateImageFile(file)
    if (error) { options.invalid(error); return }
    try {
      const uploaded = await uploader.uploadImage(file)
      if (!uploaded) { options.failed(); return }
      let position: number | undefined
      // A removed node view can no longer resolve its position. The upload still needs cleanup.
      if (!current.editor.isDestroyed && current.editor === options.editor.value) {
        try { position = current.getPosition() } catch { position = undefined }
      }
      const node = position == null ? null : current.editor.state.doc.nodeAt(position)
      if (position != null && current.editor.isEditable && node?.type.name === 'image' && node.attrs.src === current.src
        && options.replace(uploaded, file, position)) return
      if (typeof uploaded.fileId === 'number') {
        await fileApi.discardUploads([uploaded.fileId], { skipGlobalErrorHandler: true })
      }
    } catch (error) {
      if (!uploader.isAbortUploadError(error)) {
        logger.error('Image replacement failed:', error)
        options.failed()
      }
    }
  }
  return { input, busy: uploader.isUploadingImage, onChange, cancel: () => { target = null } }
}
