import { createReactiveAuthScopedQueryOptions } from '@/utils/authScopedQuery'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import type { EmoticonMaster } from '@/types/emoticon'
import { useAuthStore } from '@/stores/auth'
import { AUTH_SCOPED_QUERY_META, currentSessionQueryKey } from '@/queryAuthScope'
import { computed } from 'vue'

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
  const authStore = useAuthStore()
  return useQuery({
    ...createReactiveAuthScopedQueryOptions(authStore, computed(() => currentSessionQueryKey(authStore, emoticonQueryKeys.accessiblePicker)), async (context) => {
      const requestConfig = context.requestConfig
      const [purchasedPage, myPage] = await Promise.all([
        requestConfig
          ? emoticonApi.getPurchasedEmoticonsData({ size: 100 }, requestConfig)
          : emoticonApi.getPurchasedEmoticonsData({ size: 100 }),
        requestConfig
          ? emoticonApi.getMyEmoticonsData({ size: 100 }, requestConfig)
          : emoticonApi.getMyEmoticonsData({ size: 100 }),
      ])
      return mergeUniqueEmoticons(purchasedPage.content, myPage.content)
    }),
    enabled: isEnabled,
    meta: AUTH_SCOPED_QUERY_META,
  })
}
