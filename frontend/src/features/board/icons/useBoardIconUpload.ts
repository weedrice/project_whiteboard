import { getCurrentScope, onScopeDispose, ref, watch } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import { useI18n } from 'vue-i18n'
import { fileApi, resolveFileUploadUrl, type FileUploadResponse } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { BOARD_ICON_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'
import { useAuthStore } from '@/stores/auth'

interface UseBoardIconUploadOptions {
  setIconUrl: (iconUrl: string) => void
}

export function validateBoardIconFile(
  file: File,
  maxSizeBytes = BOARD_ICON_UPLOAD_POLICY.maxSizeBytes
): 'type' | 'size' | null {
  return validateGenericImageFile(file, {
    allowedMimeTypes: BOARD_ICON_UPLOAD_POLICY.allowedMimeTypes,
    allowedExtensions: BOARD_ICON_UPLOAD_POLICY.allowedExtensions,
    maxSizeBytes,
  })
}

async function uploadBoardIcon(file: File, config?: AxiosRequestConfig): Promise<FileUploadResponse | null> {
  const { data } = await fileApi.uploadFile(file, config)
  return data.success ? unwrapApiData(data) : null
}

export async function uploadBoardIconFile(file: File, config?: AxiosRequestConfig): Promise<string | null> {
  const uploadedFile = await uploadBoardIcon(file, config)
  return uploadedFile ? resolveFileUploadUrl(uploadedFile) : null
}

export function useBoardIconUpload({ setIconUrl }: UseBoardIconUploadOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const authStore = useAuthStore()
  const fileInput = ref<HTMLInputElement | null>(null)
  let uploadRevision = 0
  let uploadController: AbortController | null = null

  const discardStaleUpload = async (fileId: number) => {
    try {
      await fileApi.discardUploads([fileId], { skipGlobalErrorHandler: true })
    } catch (error) {
      logger.warn('Failed to discard stale board icon upload:', error)
    }
  }

  const cancelUpload = () => {
    uploadRevision += 1
    uploadController?.abort()
    uploadController = null
  }

  async function handleFileUpload(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    input.value = ''

    const validationError = validateBoardIconFile(file)
    if (validationError === 'type') {
      toastStore.addToast(t('board.form.invalidIconType'), 'error')
      return
    }
    if (validationError === 'size') {
      toastStore.addToast(t('board.form.iconTooLarge'), 'error')
      return
    }

    cancelUpload()
    const revision = uploadRevision
    const generation = authStore.sessionGeneration
    const controller = new AbortController()
    uploadController = controller

    try {
      const uploadedFile = await uploadBoardIcon(file, { signal: controller.signal })
      const stale = revision !== uploadRevision || generation !== authStore.sessionGeneration
      if (stale) {
        if (uploadedFile?.fileId) void discardStaleUpload(uploadedFile.fileId)
        return
      }
      const iconUrl = uploadedFile ? resolveFileUploadUrl(uploadedFile) : null
      if (iconUrl) {
        setIconUrl(iconUrl)
      }
    } catch (error: unknown) {
      if (controller.signal.aborted || revision !== uploadRevision || generation !== authStore.sessionGeneration) return
      logger.error('Failed to upload board icon:', error)
      toastStore.addToast(t('common.messages.error'), 'error')
    } finally {
      if (uploadController === controller) uploadController = null
    }
  }

  function chooseIconFile() {
    fileInput.value?.click()
  }

  const stopSessionWatch = watch(
    () => authStore.sessionGeneration,
    cancelUpload,
    { flush: 'sync' },
  )
  if (getCurrentScope()) onScopeDispose(() => {
    stopSessionWatch()
    cancelUpload()
  })

  return {
    fileInput,
    handleFileUpload,
    chooseIconFile,
    cancelUpload,
  }
}
