import { computed, type ComputedRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { EmoticonImage, EmoticonMaster } from '@/types/emoticon'

interface UseEmoticonEditResourceOptions {
  emoticonId: ComputedRef<number>
}

type EmoticonDetailQueryKeyId = ComputedRef<number> | number

export const emoticonDetailQueryKey = (emoticonId: EmoticonDetailQueryKeyId) => ['emoticon', emoticonId] as const
export const emoticonPurchaseStatusQueryKey = (emoticonId: EmoticonDetailQueryKeyId) => ['emoticon', emoticonId, 'purchased'] as const
export const emoticonListQueryKey = ['emoticons'] as const

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
  const { data: emoticon, isLoading } = useQuery({
    queryKey: emoticonDetailQueryKey(emoticonId),
    queryFn: async () => {
      return emoticonApi.getEmoticonData(emoticonId.value)
    },
    enabled: () => !!emoticonId.value,
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
