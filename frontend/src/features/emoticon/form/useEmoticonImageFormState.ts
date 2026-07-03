import { onUnmounted, ref } from 'vue'
import {
  revokeEmoticonPreviewUrl,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'

interface UseEmoticonImageFormStateOptions {
  selectThumbnailImage: (file: File) => Promise<EmoticonImagePreview | null>
  selectEmoticonImages: (files: FileList, remainingSlots: number) => Promise<EmoticonImagePreview[]>
  getRemainingSlots: () => number
}

function resetFileInput(input: HTMLInputElement | null): void {
  if (input) {
    input.value = ''
  }
}

export function useEmoticonImageFormState({
  selectThumbnailImage,
  selectEmoticonImages,
  getRemainingSlots
}: UseEmoticonImageFormStateOptions) {
  const thumbnailFile = ref<File | null>(null)
  const thumbnailPreview = ref<string | null>(null)
  const imagePreviews = ref<EmoticonImagePreview[]>([])
  const thumbnailInput = ref<HTMLInputElement | null>(null)
  const emoticonInput = ref<HTMLInputElement | null>(null)
  let isDisposed = false
  let thumbnailSelectionVersion = 0

  const setThumbnailPreviewFromRemote = (preview: string | null | undefined) => {
    thumbnailSelectionVersion++
    revokeEmoticonPreviewUrl(thumbnailPreview.value)
    thumbnailFile.value = null
    thumbnailPreview.value = preview || null
  }

  const handleThumbnailSelect = async (event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    const selectionVersion = ++thumbnailSelectionVersion

    try {
      const selectedThumbnail = await selectThumbnailImage(file)
      if (!selectedThumbnail) return
      if (isDisposed || selectionVersion !== thumbnailSelectionVersion) {
        revokeEmoticonPreviewUrl(selectedThumbnail.preview)
        return
      }

      revokeEmoticonPreviewUrl(thumbnailPreview.value)
      thumbnailFile.value = file
      thumbnailPreview.value = selectedThumbnail.preview
    } finally {
      resetFileInput(input)
    }
  }

  const removeThumbnail = () => {
    thumbnailSelectionVersion++
    revokeEmoticonPreviewUrl(thumbnailPreview.value)
    thumbnailFile.value = null
    thumbnailPreview.value = null
    resetFileInput(thumbnailInput.value)
  }

  const handleEmoticonSelect = async (event: Event) => {
    const input = event.target as HTMLInputElement
    const files = input.files
    if (!files) return

    try {
      const selectedImages = await selectEmoticonImages(files, getRemainingSlots())
      if (isDisposed) {
        selectedImages.forEach((item) => {
          revokeEmoticonPreviewUrl(item.preview)
        })
        return
      }

      imagePreviews.value.push(...selectedImages)
    } finally {
      resetFileInput(input)
    }
  }

  const removeImagePreview = (clientId: string) => {
    const index = imagePreviews.value.findIndex((item) => item.clientId === clientId)
    const item = index >= 0 ? imagePreviews.value[index] : null
    if (item) {
      revokeEmoticonPreviewUrl(item.preview)
      imagePreviews.value.splice(index, 1)
    }
  }

  const openThumbnailInput = () => {
    thumbnailInput.value?.click()
  }

  const openEmoticonInput = () => {
    emoticonInput.value?.click()
  }

  onUnmounted(() => {
    isDisposed = true
    revokeEmoticonPreviewUrl(thumbnailPreview.value)
    imagePreviews.value.forEach((item) => {
      revokeEmoticonPreviewUrl(item.preview)
    })
  })

  return {
    thumbnailFile,
    thumbnailPreview,
    imagePreviews,
    thumbnailInput,
    emoticonInput,
    setThumbnailPreviewFromRemote,
    handleThumbnailSelect,
    removeThumbnail,
    handleEmoticonSelect,
    removeImagePreview,
    openThumbnailInput,
    openEmoticonInput,
  }
}
