import {
  createEmoticonImagePreview,
  validateEmoticonImageFile,
  type EmoticonImagePreview,
} from '@/utils/emoticonImage'

type TranslationParams = Record<string, string | number>
type Translate = (key: string, params?: TranslationParams) => string
type ToastStore = {
  addToast: (message: string, type: 'error') => void
}

interface EmoticonImageSelectionOptions {
  getMaxCount?: () => number
}

export function useEmoticonImageSelection(
  t: Translate,
  toastStore: ToastStore,
  options: EmoticonImageSelectionOptions = {}
) {
  const selectThumbnailImage = async (file: File): Promise<EmoticonImagePreview | null> => {
    const validationError = validateEmoticonImageFile(file, { nonImageReason: 'imageOnly' })
    if (validationError === 'imageOnly') {
      toastStore.addToast(t('emoticon.validation.imageOnly'), 'error')
      return null
    }
    if (validationError === 'notImage') {
      toastStore.addToast(t('emoticon.validation.notImage', { name: file.name }), 'error')
      return null
    }
    if (validationError === 'gifSizeExceeded') {
      toastStore.addToast(t('emoticon.validation.fileSizeExceeded'), 'error')
      return null
    }

    const previewResult = await createEmoticonImagePreview(file)
    if (!previewResult.ok) {
      if (previewResult.reason === 'sizeExceeded') {
        toastStore.addToast(t('emoticon.validation.imageSizeExceeded', {
          width: previewResult.width,
          height: previewResult.height
        }), 'error')
      } else {
        toastStore.addToast(t('emoticon.validation.imageLoadFailed'), 'error')
      }
      return null
    }

    return previewResult.item
  }

  const selectEmoticonImages = async (
    files: FileList,
    remainingSlots: number
  ): Promise<EmoticonImagePreview[]> => {
    if (remainingSlots <= 0) {
      toastStore.addToast(t('emoticon.validation.maxImages', {
        count: options.getMaxCount?.() ?? 20,
      }), 'error')
      return []
    }

    const selectedImages: EmoticonImagePreview[] = []
    const filesToAdd = Array.from(files).slice(0, remainingSlots)

    for (const file of filesToAdd) {
      const validationError = validateEmoticonImageFile(file)
      if (validationError === 'notImage' || validationError === 'imageOnly') {
        toastStore.addToast(t('emoticon.validation.notImage', { name: file.name }), 'error')
        continue
      }
      if (validationError === 'gifSizeExceeded') {
        toastStore.addToast(t('emoticon.validation.fileSizeExceededNamed', { name: file.name }), 'error')
        continue
      }

      const previewResult = await createEmoticonImagePreview(file)
      if (!previewResult.ok) {
        if (previewResult.reason === 'sizeExceeded') {
          toastStore.addToast(t('emoticon.validation.imageSizeExceededNamed', {
            name: file.name,
            width: previewResult.width,
            height: previewResult.height
          }), 'error')
        } else {
          toastStore.addToast(t('emoticon.validation.loadFailedNamed', { name: file.name }), 'error')
        }
        continue
      }

      selectedImages.push(previewResult.item)
    }

    return selectedImages
  }

  return {
    selectThumbnailImage,
    selectEmoticonImages,
  }
}
