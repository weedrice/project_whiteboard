import { createReactiveAuthScopedQueryOptions, type AuthScopedQueryContext } from '@/utils/authScopedQuery'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import type { EmoticonMaster } from '@/types/emoticon'
import { useAuthStore } from '@/stores/auth'
import { AUTH_SCOPED_QUERY_META, currentSessionQueryKey } from '@/queryAuthScope'
import { computed } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import type { PageResponse } from '@/types/common'

async function loadEmoticonPages(
  request: (params: { page: number; size: number }, config: AxiosRequestConfig) => Promise<PageResponse<EmoticonMaster>>,
  context: AuthScopedQueryContext,
) {
  const items: EmoticonMaster[] = []
  let totalPages = 1
  for (let page = 0; page < totalPages; page += 1) {
    context.assertCurrent()
    context.signal.throwIfAborted()
    const result = await request({ page, size: 100 }, context.requestConfig)
    context.assertCurrent()
    context.signal.throwIfAborted()
    if (result.number !== page || !Number.isSafeInteger(result.totalPages) || result.totalPages < 0) {
      throw new Error('Invalid emoticon page metadata')
    }
    // Keep a finite snapshot bound even if new packs are added during the load.
    if (page === 0) totalPages = result.totalPages
    items.push(...result.content)
    if (result.last || result.content.length === 0) break
  }
  return items
}

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
      const [purchased, owned] = await Promise.all([
        loadEmoticonPages(emoticonApi.getPurchasedEmoticonsData, context),
        loadEmoticonPages(emoticonApi.getMyEmoticonsData, context),
      ])
      return mergeUniqueEmoticons(purchased, owned)
    }),
    enabled: isEnabled,
    meta: AUTH_SCOPED_QUERY_META,
  })
}
