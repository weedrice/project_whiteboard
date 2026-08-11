import { ref } from 'vue'
import { hasCandidateImageFiles, isCandidateImageFile } from '@/components/board/editor/postEditorImageFiles'

type ImageUploadQueue = {
  enqueueFiles: (files: File[]) => void
}

export function usePostEditorImageFiles(
  imageUploadQueue: ImageUploadQueue,
  insertPreservedHtml?: (html: string) => boolean,
) {
  const imageInput = ref<HTMLInputElement | null>(null)
  const isDraggingImage = ref(false)

  function triggerImageUpload() {
    imageInput.value?.click()
  }

  function queueImageFiles(files: File[]) {
    const candidateFiles = files.filter(isCandidateImageFile)
    if (candidateFiles.length === 0) return false
    imageUploadQueue.enqueueFiles(candidateFiles)
    return true
  }

  async function onImageChange(event: Event) {
    const input = event.target as HTMLInputElement
    const files = Array.from(input.files ?? [])
    if (files.length === 0) return

    try {
      queueImageFiles(files)
    } finally {
      input.value = ''
    }
  }

  function onEditorPaste(event: ClipboardEvent) {
    const files = Array.from(event.clipboardData?.files ?? [])
    if (queueImageFiles(files)) {
      event.preventDefault()
      return
    }

    const html = event.clipboardData?.getData('text/html') ?? ''
    if (html && insertPreservedHtml?.(html)) {
      event.preventDefault()
    }
  }

  function onEditorDrop(event: DragEvent) {
    const files = Array.from(event.dataTransfer?.files ?? [])
    isDraggingImage.value = false
    if (queueImageFiles(files)) {
      event.preventDefault()
    }
  }

  function onEditorDragEnter(event: DragEvent) {
    const files = Array.from(event.dataTransfer?.items ?? [])
      .filter((item) => item.kind === 'file')
      .map((item) => item.getAsFile())
      .filter((file): file is File => Boolean(file))
    if (files.length > 0 && hasCandidateImageFiles(files)) {
      isDraggingImage.value = true
    }
  }

  function onEditorDragLeave(event: DragEvent) {
    const currentTarget = event.currentTarget as HTMLElement
    const relatedTarget = event.relatedTarget as Node | null
    if (!relatedTarget || !currentTarget.contains(relatedTarget)) {
      isDraggingImage.value = false
    }
  }

  return {
    imageInput,
    isDraggingImage,
    triggerImageUpload,
    onImageChange,
    onEditorPaste,
    onEditorDrop,
    onEditorDragEnter,
    onEditorDragLeave,
  }
}
