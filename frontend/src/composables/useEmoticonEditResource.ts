import { type ComputedRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { QUERY_STALE_TIME } from '@/utils/constants'

interface UseEmoticonEditResourceOptions {
  emoticonId: ComputedRef<number>
}

type EmoticonDetailQueryKeyId = ComputedRef<number> | number

export const emoticonDetailQueryKey = (emoticonId: EmoticonDetailQueryKeyId) => ['emoticon', emoticonId] as const

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

  return {
    emoticon,
    isLoading,
  }
}
