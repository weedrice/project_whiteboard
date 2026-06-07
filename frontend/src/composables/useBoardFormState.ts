import { ref, watch, type Ref } from 'vue'
import { normalizeBoardUrlInput } from '@/utils/board'
import { useObjectUrlPreview } from '@/composables/useObjectUrlPreview'

export interface BoardFormData {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: number
  allowNsfw: boolean
  isPublic: boolean
  agentUseYn: boolean
  guidePrompt: string
}

interface UseBoardFormStateOptions {
  initialData: () => BoardFormData
  isEdit: () => boolean
}

export function useBoardFormState(options: UseBoardFormStateOptions) {
  const form = ref<BoardFormData>({ ...options.initialData() }) as Ref<BoardFormData>
  const selectedFile = ref<File | null>(null)
  const {
    previewUrl: previewImage,
    setPreviewUrl,
    setPreviewFile,
  } = useObjectUrlPreview()

  watch(options.initialData, (newData) => {
    form.value = { ...newData }
    selectedFile.value = null
    if (!form.value.isPublic) {
      form.value.agentUseYn = false
    }
    setPreviewUrl(newData.iconUrl || null)
  }, { deep: true, immediate: true })

  watch(() => form.value.isPublic, (isPublic) => {
    if (!isPublic) {
      form.value.agentUseYn = false
    }
  })

  watch(() => form.value.boardUrl, (boardUrl) => {
    if (options.isEdit()) return

    const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
    if (boardUrl !== normalizedBoardUrl) {
      form.value.boardUrl = normalizedBoardUrl
    }
  })

  function handleFileChange(event: Event) {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return

    selectedFile.value = file
    setPreviewFile(file)
  }

  return {
    form,
    selectedFile,
    previewImage,
    handleFileChange,
  }
}
