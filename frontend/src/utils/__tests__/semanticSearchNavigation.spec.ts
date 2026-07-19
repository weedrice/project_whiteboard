import { describe, expect, it } from 'vitest'
import { resolveSemanticSearchResultRoute } from '@/utils/semanticSearchNavigation'
import type { SemanticSearchResult } from '@/types'

const result = (contentType: 'POST' | 'COMMENT'): SemanticSearchResult => ({
  contentType,
  contentId: 17,
  postId: 9,
  boardId: 3,
  boardUrl: 'general',
  boardName: 'General',
  title: 'Result',
  excerpt: 'Excerpt',
  createdAt: '2026-07-19T00:00:00',
})

describe('resolveSemanticSearchResultRoute', () => {
  it('links comment matches to the exact comment anchor', () => {
    expect(resolveSemanticSearchResultRoute(result('COMMENT'))).toEqual({
      name: 'post-detail',
      params: { boardUrl: 'general', postId: 9 },
      hash: '#comment-17',
    })
  })

  it('links post matches without a comment anchor', () => {
    expect(resolveSemanticSearchResultRoute(result('POST'))).toEqual({
      name: 'post-detail',
      params: { boardUrl: 'general', postId: 9 },
    })
  })
})
