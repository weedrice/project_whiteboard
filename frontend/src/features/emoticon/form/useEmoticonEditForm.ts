import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { EmoticonImage, EmoticonMaster } from '@/types/emoticon'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'
import { useEmoticonImageFormState } from '@/features/emoticon/form/useEmoticonImageFormState'
import type { EmoticonEditFormState } from '@/features/emoticon/form/useEmoticonEditResource'
import { useEmoticonTags } from '@/features/emoticon/form/useEmoticonTags'
import { useToggleEmoticonVisibility } from '@/features/emoticon/useToggleEmoticonVisibility'
import { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'

interface UseEmoticonEditFormOptions {
  emoticonId: ComputedRef<number>
  emoticon: Ref<EmoticonMaster | null | undefined>
  editFormState: ComputedRef<EmoticonEditFormState | null>
  selectThumbnailImage: (file: File) => Promise<EmoticonImagePreview | null>
  selectEmoticonImages: (files: FileList, remainingSlots: number) => Promise<EmoticonImagePreview[]>
  confirm: (message: string) => Promise<boolean>
  t: (key: string) => string
  onMaxTags: () => void
  maxImageCount: ComputedRef<number>
}

export function useEmoticonEditForm({
  emoticonId,
  emoticon,
  editFormState,
  selectThumbnailImage,
  selectEmoticonImages,
  confirm,
  t,
  onMaxTags,
  maxImageCount,
}: UseEmoticonEditFormOptions) {
  const emoticonName = ref('')
  const originalThumbnailUrl = ref<string | null>(null)
  const existingImages = ref<EmoticonImage[]>([])
  const imagesToDelete = ref<number[]>([])
  const hydratedEmoticonId = ref<number | null>(null)
  const isSubmitting = ref(false)
  const uploadSession = useEmoticonUploadSession()
  const { uploadProgress } = uploadSession
  const { tagInput, tagItems, tags, addTag, removeTag } = useEmoticonTags({
    onMaxTags,
  })

  const {
    thumbnailFile,
    thumbnailPreview,
    imagePreviews: newEmoticonPreviews,
    thumbnailInput,
    emoticonInput,
    setThumbnailPreviewFromRemote,
    handleThumbnailSelect,
    handleEmoticonSelect,
    removeImagePreview: removeNewEmoticonImage,
    openThumbnailInput,
  } = useEmoticonImageFormState({
    selectThumbnailImage,
    selectEmoticonImages,
    getRemainingSlots: () => existingImages.value.length > maxImageCount.value
      ? 0
      : Math.max(0, maxImageCount.value - totalImageCount.value),
  })

  const changeThumbnail = openThumbnailInput

  watch(editFormState, (formState) => {
    if (!formState) return

    const currentEmoticonId = emoticonId.value
    if (formState.emoticonId !== currentEmoticonId || hydratedEmoticonId.value === currentEmoticonId) return

    hydratedEmoticonId.value = currentEmoticonId
    emoticonName.value = formState.name
    tags.value = [...formState.tags]
    existingImages.value = [...formState.existingImages]
    originalThumbnailUrl.value = formState.thumbnailUrl
    setThumbnailPreviewFromRemote(formState.thumbnailUrl)
  }, { immediate: true })

  const { mutate: toggleVisibility, isPending: isToggling } = useToggleEmoticonVisibility(emoticonId)

  const handleToggleVisibility = async () => {
    if (!emoticon.value) return
    const targetEmoticonId = emoticonId.value
    const verb = emoticon.value.isActive ? t('emoticon.visibility.hideConfirm') : t('emoticon.visibility.showConfirm')
    const isConfirmed = await confirm(verb)
    if (!isConfirmed || emoticonId.value !== targetEmoticonId) return

    toggleVisibility()
  }

  const markImageForDeletion = (imageId: number) => {
    if (!imagesToDelete.value.includes(imageId)) {
      imagesToDelete.value.push(imageId)
    }
  }

  const unmarkImageForDeletion = (imageId: number) => {
    const index = imagesToDelete.value.indexOf(imageId)
    if (index > -1) {
      imagesToDelete.value.splice(index, 1)
    }
  }

  const totalImageCount = computed(() => {
    const existingCount = existingImages.value.filter(img => !imagesToDelete.value.includes(img.imageId)).length
    return existingCount + newEmoticonPreviews.value.length
  })

  const canAddImages = computed(() => (
    existingImages.value.length <= maxImageCount.value
      && totalImageCount.value < maxImageCount.value
  ))

  const isFormValid = computed(() => {
    return emoticonName.value.trim() !== ''
      && thumbnailPreview.value !== null
      && totalImageCount.value > 0
      && (totalImageCount.value <= maxImageCount.value || newEmoticonPreviews.value.length === 0)
  })

  return {
    emoticonName,
    originalThumbnailUrl,
    existingImages,
    imagesToDelete,
    hydratedEmoticonId,
    isSubmitting,
    uploadSession,
    uploadProgress,
    tagInput,
    tagItems,
    tags,
    addTag,
    removeTag,
    thumbnailFile,
    thumbnailPreview,
    newEmoticonPreviews,
    thumbnailInput,
    emoticonInput,
    handleThumbnailSelect,
    handleEmoticonSelect,
    removeNewEmoticonImage,
    changeThumbnail,
    isToggling,
    handleToggleVisibility,
    markImageForDeletion,
    unmarkImageForDeletion,
    totalImageCount,
    canAddImages,
    isFormValid,
  }
}
