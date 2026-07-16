import { computed, type ComputedRef } from 'vue'
import type { AxiosResponse } from 'axios'
import { emoticonApi } from '@/api/emoticon'
import { useApiQuery } from '@/composables/useApiQuery'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { ApiResponse } from '@/types'
import type { EmoticonImage, EmoticonMaster } from '@/types/emoticon'

interface UseEmoticonEditResourceOptions {
  emoticonId: ComputedRef<number>
}

export interface EmoticonEditFormState {
  emoticonId: number
  name: string
  thumbnailUrl: string | null
  tags: string[]
  existingImages: EmoticonImage[]
}

export function toEmoticonEditFormState(emoticon: EmoticonMaster): EmoticonEditFormState {
  return {
    emoticonId: emoticon.emoticonId,
    name: emoticon.name || '',
    thumbnailUrl: emoticon.thumbnailUrl || null,
    tags: [...(emoticon.tags || [])],
    existingImages: [...(emoticon.images || [])],
  }
}

export function useEmoticonEditResource({
  emoticonId,
}: UseEmoticonEditResourceOptions) {
  const { data: emoticon, isLoading } = useApiQuery({
    queryKey: computed(() => emoticonQueryKeys.detail(emoticonId.value)),
    request: (context) => callWithOptionalQuerySignal(
      context,
      () => emoticonApi.getEmoticon(emoticonId.value),
      (config) => emoticonApi.getEmoticon(emoticonId.value, config),
    ) as Promise<AxiosResponse<ApiResponse<EmoticonMaster>>>,
    enabled: computed(() => !!emoticonId.value),
    staleTime: QUERY_STALE_TIME.SHORT,
  })
  const editFormState = computed(() => {
    if (!emoticon.value) return null

    return toEmoticonEditFormState(emoticon.value)
  })

  return {
    emoticon,
    editFormState,
    isLoading,
  }
}
