import { computed, ref, watch, type Ref } from 'vue'
import { getOptimizedProfileImageUrl } from '@/utils/image'
import { useObjectUrlPreview } from '@/composables/useObjectUrlPreview'
import { resizeImageToBoundsFile } from '@/utils/imageFile'
import logger from '@/utils/logger'

interface UseProfileImageEditorOptions {
  profileImageUrl: () => string | null | undefined
  onFileSizeExceeded: () => void
  onProcessFailed: () => void
}

const MAX_PROFILE_IMAGE_SIZE = 10 * 1024 * 1024
const PROFILE_IMAGE_MAX_WIDTH = 100
const PROFILE_IMAGE_MAX_HEIGHT = 100

export function useProfileImageEditor(options: UseProfileImageEditorOptions): {
  fileInputRef: Ref<HTMLInputElement | null>
  selectedFile: Ref<File | null>
  profileImageError: Ref<boolean>
  profileImageDisplayUrl: Ref<string>
  handleFileChange: (event: Event) => Promise<void>
} {
  const fileInputRef = ref<HTMLInputElement | null>(null)
  const selectedFile = ref<File | null>(null)
  const {
    previewUrl: previewImage,
    setPreviewFile,
  } = useObjectUrlPreview()
  const profileImageError = ref(false)

  const profileImageDisplayUrl = computed(() => {
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

    if (file.size > MAX_PROFILE_IMAGE_SIZE) {
      options.onFileSizeExceeded()
      return
    }

    try {
      const resizedImage = await resizeImageToBoundsFile(file, PROFILE_IMAGE_MAX_WIDTH, PROFILE_IMAGE_MAX_HEIGHT)
      selectedFile.value = resizedImage
      setPreviewFile(resizedImage)
    } catch (error) {
      logger.error('Image resize failed', error)
      options.onProcessFailed()
    }
  }

  return {
    fileInputRef,
    selectedFile,
    profileImageError,
    profileImageDisplayUrl,
    handleFileChange
  }
}
