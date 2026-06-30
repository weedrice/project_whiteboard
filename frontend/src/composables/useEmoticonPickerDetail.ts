import { computed, ref } from 'vue'
import { emoticonApi } from '@/api/emoticon'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import type { EmoticonMaster } from '@/types/emoticon'
import logger from '@/utils/logger'

export const useEmoticonPickerDetail = (translate: (key: string) => string) => {
  const selectedEmoticon = ref<EmoticonMaster | null>(null)
  const selectedEmoticonId = ref<number | null>(null)
  const selectedEmoticonSource = ref<EmoticonMaster | null>(null)

  const detailTask = useLatestAsyncTask<string>({
    getErrorValue: () => translate('emoticon.picker.detailLoadFailed'),
    onError: (error) => {
      logger.error('Failed to load emoticon detail:', error)
    },
  })

  const selectedImages = computed(() => selectedEmoticon.value?.images || [])

  const resetDetailState = () => {
    detailTask.reset()
    selectedEmoticon.value = null
    selectedEmoticonId.value = null
    selectedEmoticonSource.value = null
  }

  const handleEmoticonClick = async (emoticon: EmoticonMaster) => {
    selectedEmoticonId.value = emoticon.emoticonId
    selectedEmoticonSource.value = emoticon
    selectedEmoticon.value = null

    const emoticonDetail = await detailTask.run(({ signal }) => (
      emoticonApi.getEmoticonData(emoticon.emoticonId, { signal })
    ))
    if (!emoticonDetail || selectedEmoticonId.value !== emoticon.emoticonId) return
    selectedEmoticon.value = emoticonDetail
  }

  const retryDetailLoad = () => {
    if (!selectedEmoticonSource.value) return
    void handleEmoticonClick(selectedEmoticonSource.value)
  }

  return {
    selectedEmoticon,
    selectedEmoticonId,
    selectedImages,
    isLoadingDetail: detailTask.loading,
    detailError: detailTask.error,
    resetDetailState,
    handleEmoticonClick,
    retryDetailLoad,
  }
}
