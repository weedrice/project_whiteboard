import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'

interface UseBoardIconUploadOptions {
  setIconUrl: (iconUrl: string) => void
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
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return

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
