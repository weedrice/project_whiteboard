import { onBeforeUnmount, provide, ref, watch, type InjectionKey, type Ref } from 'vue'
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
  const currentFileName = ref('')
  let active: { cancelled: boolean } | null = null
  function cancel() {
    target = null
    if (active) active.cancelled = true
    uploader.abortImageUpload()
  }
  watch(options.ownerIdentity, cancel, { flush: 'sync' })
  onBeforeUnmount(cancel)
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
    const operation = { cancelled: false }
    active = operation
    currentFileName.value = file.name
    try {
      const uploaded = await uploader.uploadImage(file)
      if (!uploaded) {
        if (!operation.cancelled) options.failed()
        return
      }
      let position: number | undefined
      // A removed node view can no longer resolve its position. The upload still needs cleanup.
      if (!operation.cancelled && !current.editor.isDestroyed && current.editor === options.editor.value) {
        try { position = current.getPosition() } catch { position = undefined }
      }
      const node = position == null ? null : current.editor.state.doc.nodeAt(position)
      if (position != null && current.editor.isEditable && node?.type.name === 'image' && node.attrs.src === current.src
        && options.replace(uploaded, file, position)) return
      if (typeof uploaded.fileId === 'number') {
        await fileApi.discardUploads([uploaded.fileId], { skipGlobalErrorHandler: true })
      }
    } catch (error) {
      if (!operation.cancelled && !uploader.isAbortUploadError(error)) {
        logger.error('Image replacement failed:', error)
        options.failed()
      }
    } finally {
      if (active === operation) {
        active = null
        currentFileName.value = ''
      }
    }
  }
  return { input, busy: uploader.isUploadingImage, currentFileName, onChange, cancel }
}
