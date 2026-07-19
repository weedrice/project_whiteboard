import type { RouteLocationRaw } from 'vue-router'
import type { SemanticSearchResult } from '@/types'

export function resolveSemanticSearchResultRoute(result: SemanticSearchResult): RouteLocationRaw {
  return {
    name: 'post-detail',
    params: {
      boardUrl: result.boardUrl,
      postId: result.postId,
    },
    ...(result.contentType === 'COMMENT'
      ? { hash: `#comment-${result.contentId}` }
      : {}),
  }
}
