import { computed, ref, watch, type Ref } from 'vue'
import { getOptimizedProfileImageUrl } from '@/utils/image'
import { useObjectUrlPreview } from '@/composables/useObjectUrlPreview'
import { resizeImageToBoundsFile, validateImageFile } from '@/utils/imageFile'
import { PROFILE_IMAGE_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'
import logger from '@/utils/logger'

interface UseProfileImageEditorOptions {
  profileImageUrl: () => string | null | undefined
  onFileSizeExceeded: () => void
  onProcessFailed: () => void
}

export function useProfileImageEditor(options: UseProfileImageEditorOptions): {
  fileInputRef: Ref<HTMLInputElement | null>
  selectedFile: Ref<File | null>
  removeProfileImage: Ref<boolean>
  profileImageError: Ref<boolean>
  profileImageDisplayUrl: Ref<string>
  handleFileChange: (event: Event) => Promise<void>
  clearProfileImage: () => void
  resetProfileImageDraft: () => void
} {
  const fileInputRef = ref<HTMLInputElement | null>(null)
  const selectedFile = ref<File | null>(null)
  const removeProfileImage = ref(false)
  const {
    previewUrl: previewImage,
    setPreviewFile,
    clearPreviewUrl,
  } = useObjectUrlPreview()
  const profileImageError = ref(false)
  let fileSelectionVersion = 0

  const profileImageDisplayUrl = computed(() => {
    if (removeProfileImage.value) return ''
    if (previewImage.value) return previewImage.value
    if (profileImageError.value || !options.profileImageUrl()) return ''
    return getOptimizedProfileImageUrl(options.profileImageUrl() || '')
  })

  watch(options.profileImageUrl, () => {
    profileImageError.value = false
  })

  const handleFileChange = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return
    const selectionVersion = ++fileSelectionVersion
    target.value = ''

    const validationError = validateImageFile(file, PROFILE_IMAGE_UPLOAD_POLICY)
    if (validationError === 'size') {
      options.onFileSizeExceeded()
      return
    }
    if (validationError === 'type') {
      options.onProcessFailed()
      return
    }

    try {
      const resizedImage = await resizeImageToBoundsFile(
        file,
        PROFILE_IMAGE_UPLOAD_POLICY.maxWidth ?? 100,
        PROFILE_IMAGE_UPLOAD_POLICY.maxHeight ?? 100,
      )
      if (selectionVersion !== fileSelectionVersion) {
        return
      }
      removeProfileImage.value = false
      selectedFile.value = resizedImage
      setPreviewFile(resizedImage)
    } catch (error) {
      logger.error('Image resize failed', error)
      options.onProcessFailed()
    }
  }

  const clearProfileImage = () => {
    fileSelectionVersion++
    selectedFile.value = null
    removeProfileImage.value = true
    profileImageError.value = false
    clearPreviewUrl()
  }

  const resetProfileImageDraft = () => {
    fileSelectionVersion++
    selectedFile.value = null
    removeProfileImage.value = false
    profileImageError.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
    clearPreviewUrl()
  }

  return {
    fileInputRef,
    selectedFile,
    removeProfileImage,
    profileImageError,
    profileImageDisplayUrl,
    handleFileChange,
    clearProfileImage,
    resetProfileImageDraft,
  }
}
