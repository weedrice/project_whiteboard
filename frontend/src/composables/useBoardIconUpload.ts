import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fileApi } from '@/api/file'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'

interface UseBoardIconUploadOptions {
  setIconUrl: (iconUrl: string) => void
}

export function useBoardIconUpload({ setIconUrl }: UseBoardIconUploadOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const fileInput = ref<HTMLInputElement | null>(null)

  async function handleFileUpload(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return

    try {
      const { data } = await fileApi.uploadFile(file)

      if (data.success) {
        setIconUrl(data.data.url)
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
