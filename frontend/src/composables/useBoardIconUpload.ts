import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { BOARD_ICON_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'

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

export async function uploadBoardIconFile(file: File): Promise<string | null> {
  const { data } = await fileApi.uploadFile(file)
  return data.success ? resolveFileUploadUrl(unwrapApiData(data)) : null
}

export function useBoardIconUpload({ setIconUrl }: UseBoardIconUploadOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const fileInput = ref<HTMLInputElement | null>(null)

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

    try {
      const iconUrl = await uploadBoardIconFile(file)
      if (iconUrl) {
        setIconUrl(iconUrl)
      }
    } catch (error: unknown) {
      logger.error('Failed to upload board icon:', error)
      toastStore.addToast(t('common.messages.error'), 'error')
    }
  }

  function chooseIconFile() {
    fileInput.value?.click()
  }

  return {
    fileInput,
    handleFileUpload,
    chooseIconFile
  }
}
