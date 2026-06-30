import { onScopeDispose, ref } from 'vue'
import { revokeBlobUrlIfNeeded } from '@/utils/imageFile'

export function useObjectUrlPreview(initialUrl: string | null = null) {
  const previewUrl = ref<string | null>(initialUrl)

  const setPreviewUrl = (nextUrl: string | null) => {
    const previousUrl = previewUrl.value
    if (previousUrl === nextUrl) {
      return
    }

    previewUrl.value = nextUrl
    revokeBlobUrlIfNeeded(previousUrl)
  }

  const setPreviewFile = (file: Blob) => {
    setPreviewUrl(URL.createObjectURL(file))
  }

  const clearPreviewUrl = () => {
    setPreviewUrl(null)
  }

  onScopeDispose(() => {
    revokeBlobUrlIfNeeded(previewUrl.value)
  }, true)

  return {
    previewUrl,
    setPreviewUrl,
    setPreviewFile,
    clearPreviewUrl,
  }
}
