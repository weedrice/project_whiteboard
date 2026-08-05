import { describe, expect, it } from 'vitest'
import { createStoredSavedDraftSnapshot } from '@/features/board/posts/draft/postDraftSnapshot'

describe('postDraftSnapshot', () => {
  it('keeps recovered contents when the server reset stale references', () => {
    const snapshot = createStoredSavedDraftSnapshot({
      boardUrl: 'free',
      title: 'Draft',
      contents: '<p>Body</p>',
      fileIds: [],
    }, {
      draftId: 91,
      boardId: 1,
      boardUrl: 'free',
      boardName: 'Free',
      title: 'Draft',
      contents: '<p>Body</p><img src="/api/v1/files/7">',
      tags: [],
      fileIds: [],
      isNotice: false,
      isNsfw: false,
      isSpoiler: false,
      isSecret: false,
      staleReferencesReset: true,
    })

    expect(snapshot.contents).toBe('<p>Body</p>')
  })
})
