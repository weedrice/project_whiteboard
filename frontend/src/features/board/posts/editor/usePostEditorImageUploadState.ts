import { computed, watch, type Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import {
  useEditorImageUploadQueue,
  type EditorImageUploadItem,
} from '@/composables/useEditorImageUploadQueue'

interface UsePostEditorImageUploadStateOptions<TUploaded> {
  validate: (file: File) => 'type' | 'size' | null
  upload: (file: File, config?: AxiosRequestConfig) => Promise<TUploaded | null>
  ownerIdentity?: Readonly<Ref<string | undefined>>
  isAbort: (error: unknown) => boolean
  onUploaded: (uploaded: TUploaded, file: File, itemId: number) => void
  onItemReleased?: (itemId: number) => void
  onValidationError?: (error: 'type' | 'size', file: File) => void
  onFailed: (error: unknown, file: File) => void
  abort: () => void
}

export function usePostEditorImageUploadState<TUploaded>({
  validate,
  upload,
  isAbort,
  onUploaded,
  onItemReleased,
  onValidationError,
  onFailed,
  abort,
  ownerIdentity,
}: UsePostEditorImageUploadStateOptions<TUploaded>) {
  const imageUploadQueue = useEditorImageUploadQueue<TUploaded>({
    validate,
    authSource: useAuthStore(),
    upload,
    isAbort,
    onUploaded,
    onItemReleased,
    onValidationError: onValidationError
      ? (error, file) => {
        if (error === 'type' || error === 'size') {
          onValidationError(error, file)
        }
      }
      : undefined,
    onFailed,
    abort,
  })

  useAuthSessionReset(imageUploadQueue.clear)
  if (ownerIdentity) watch(ownerIdentity, imageUploadQueue.clear, { flush: 'sync' })

  const hasImageUploadError = computed(() => imageUploadQueue.failedCount.value > 0)
  const failedImageCount = computed(() => imageUploadQueue.failedCount.value)
  const failedImageFiles = computed(() => imageUploadQueue.failedItems.value.map((item) => item.file))
  const currentUploadingImageName = computed(() => imageUploadQueue.currentItem.value?.file.name ?? '')
  const imageUploadQueueCount = computed(() => imageUploadQueue.queueCount.value)

  const retryImageUpload = () => {
    imageUploadQueue.retryNextFailed()
  }

  const findFailedImageUploadItem = (file: File): EditorImageUploadItem<TUploaded> | undefined => (
    imageUploadQueue.failedItems.value.find((item) => item.file === file)
  )

  const retryFailedImageUpload = (file: File) => {
    const item = findFailedImageUploadItem(file)
    if (item) imageUploadQueue.retryItem(item)
  }

  const dismissImageUploadError = () => {
    imageUploadQueue.dismissFailed()
  }

  const dismissFailedImageUpload = (file: File) => {
    const item = findFailedImageUploadItem(file)
    if (item) imageUploadQueue.dismissItem(item)
  }

  const cancelImageUpload = () => {
    imageUploadQueue.cancel()
  }

  return {
    imageUploadQueue,
    hasImageUploadError,
    failedImageCount,
    failedImageFiles,
    currentUploadingImageName,
    imageUploadQueueCount,
    retryImageUpload,
    retryFailedImageUpload,
    dismissImageUploadError,
    dismissFailedImageUpload,
    cancelImageUpload,
  }
}
