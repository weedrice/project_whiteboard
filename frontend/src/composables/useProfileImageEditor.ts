import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { getOptimizedProfileImageUrl } from '@/utils/image'
import logger from '@/utils/logger'

interface UseProfileImageEditorOptions {
  profileImageUrl: () => string | null | undefined
  onFileSizeExceeded: () => void
  onProcessFailed: () => void
}

const MAX_PROFILE_IMAGE_SIZE = 10 * 1024 * 1024
const PROFILE_IMAGE_MAX_WIDTH = 100
const PROFILE_IMAGE_MAX_HEIGHT = 100

const revokeObjectUrlIfNeeded = (url: string | null | undefined) => {
  if (url && url.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

const resizeImage = (file: File, maxWidth: number, maxHeight: number): Promise<File> => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const objectUrl = URL.createObjectURL(file)
    img.src = objectUrl
    img.onload = () => {
      const canvas = document.createElement('canvas')
      let width = img.width
      let height = img.height

      if (width > height) {
        if (width > maxWidth) {
          height *= maxWidth / width
          width = maxWidth
        }
      } else if (height > maxHeight) {
        width *= maxHeight / height
        height = maxHeight
      }

      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')

      if (!ctx) {
        URL.revokeObjectURL(objectUrl)
        reject(new Error('Could not get canvas context'))
        return
      }

      ctx.drawImage(img, 0, 0, width, height)
      canvas.toBlob((blob) => {
        URL.revokeObjectURL(objectUrl)
        if (blob) {
          resolve(new File([blob], file.name, { type: file.type }))
          return
        }
        reject(new Error('Canvas to Blob failed'))
      }, file.type)
    }

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('Image load failed'))
    }
  })
}

export function useProfileImageEditor(options: UseProfileImageEditorOptions): {
  fileInputRef: Ref<HTMLInputElement | null>
  selectedFile: Ref<File | null>
  profileImageError: Ref<boolean>
  profileImageDisplayUrl: Ref<string>
  handleFileChange: (event: Event) => Promise<void>
} {
  const fileInputRef = ref<HTMLInputElement | null>(null)
  const selectedFile = ref<File | null>(null)
  const previewImage = ref<string | null>(null)
  const profileImageError = ref(false)

  const profileImageDisplayUrl = computed(() => {
    if (previewImage.value) return previewImage.value
    if (profileImageError.value || !options.profileImageUrl()) return ''
    return getOptimizedProfileImageUrl(options.profileImageUrl() || '')
  })

  watch(options.profileImageUrl, () => {
    profileImageError.value = false
  })

  watch(previewImage, (_newUrl, oldUrl) => {
    revokeObjectUrlIfNeeded(oldUrl)
  })

  onUnmounted(() => {
    revokeObjectUrlIfNeeded(previewImage.value)
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
      const resizedImage = await resizeImage(file, PROFILE_IMAGE_MAX_WIDTH, PROFILE_IMAGE_MAX_HEIGHT)
      selectedFile.value = resizedImage
      previewImage.value = URL.createObjectURL(resizedImage)
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
