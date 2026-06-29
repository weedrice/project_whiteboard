import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { accessibleEmoticonPickerQueryKey } from '@/composables/useEmoticonEditResource'
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
    queryFn: async () => {
      const [purchasedPage, myPage] = await Promise.all([
        emoticonApi.getPurchasedEmoticonsData({ size: 100 }),
        emoticonApi.getMyEmoticonsData({ size: 100 }),
      ])
      return mergeUniqueEmoticons(purchasedPage.content, myPage.content)
    },
    enabled: isEnabled,
  })
}
