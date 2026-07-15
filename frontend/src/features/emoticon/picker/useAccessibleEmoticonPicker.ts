import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { accessibleEmoticonPickerQueryKey } from '@/features/emoticon/form/useEmoticonEditResource'
import type { EmoticonMaster } from '@/types/emoticon'

export function mergeUniqueEmoticons(...groups: EmoticonMaster[][]) {
  const seen = new Set<number>()
  return groups.flat().filter((emoticon) => {
    if (seen.has(emoticon.emoticonId)) {
      return false
    }
    seen.add(emoticon.emoticonId)
    return true
  })
}

export function useAccessibleEmoticonPicker(isEnabled: () => boolean) {
  return useQuery({
    queryKey: accessibleEmoticonPickerQueryKey,
    queryFn: async (context?: { signal?: AbortSignal }) => {
      const requestConfig = context?.signal ? { signal: context.signal } : undefined
      const [purchasedPage, myPage] = await Promise.all([
        requestConfig
          ? emoticonApi.getPurchasedEmoticonsData({ size: 100 }, requestConfig)
          : emoticonApi.getPurchasedEmoticonsData({ size: 100 }),
        requestConfig
          ? emoticonApi.getMyEmoticonsData({ size: 100 }, requestConfig)
          : emoticonApi.getMyEmoticonsData({ size: 100 }),
      ])
      return mergeUniqueEmoticons(purchasedPage.content, myPage.content)
    },
    enabled: isEnabled,
  })
}
