import { computed, type ComputedRef } from 'vue'
import type { AxiosResponse } from 'axios'
import { emoticonApi } from '@/api/emoticon'
import { useApiQuery } from '@/composables/useApiQuery'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { ApiResponse } from '@/types'
import type { EmoticonImage, EmoticonMaster } from '@/types/emoticon'

interface UseEmoticonEditResourceOptions {
  emoticonId: ComputedRef<number>
}

type EmoticonDetailQueryKeyId = ComputedRef<number> | number

export const emoticonDetailQueryKey = (emoticonId: EmoticonDetailQueryKeyId) => ['emoticon', emoticonId] as const
export const emoticonPurchaseStatusQueryKey = (emoticonId: EmoticonDetailQueryKeyId) => ['emoticon', emoticonId, 'purchased'] as const
export const emoticonListQueryKey = ['emoticons'] as const
export const popularEmoticonsQueryKey = (period: unknown) => [...emoticonListQueryKey, 'popular', period] as const
export const searchableEmoticonsQueryKey = (
  page: unknown,
  sortBy: unknown,
  keyword: unknown,
  searchType: unknown,
) => [...emoticonListQueryKey, 'list', page, sortBy, keyword, searchType] as const
export const accessibleEmoticonPickerQueryKey = [...emoticonListQueryKey, 'accessible', 'picker'] as const

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
    queryKey: emoticonDetailQueryKey(emoticonId),
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
