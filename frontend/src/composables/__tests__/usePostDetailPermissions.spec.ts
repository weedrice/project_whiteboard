import { ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { usePostDetailPermissions } from '../usePostDetailPermissions'
import type { Post } from '@/types'

const createPost = (overrides: Partial<Post> = {}): Post => ({
  postId: 15,
  title: 'Test post',
  contents: '<p>body</p>',
  viewCount: 12,
  likeCount: 4,
  commentCount: 2,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  author: {
    userId: 7,
    displayName: 'Author',
    authorType: 'USER'
  },
  board: {
    boardId: 1,
    boardName: 'Free board',
    boardUrl: 'free',
    isAdmin: false
  },
  createdAt: '2026-04-22T10:00:00',
  ...overrides
})

describe('usePostDetailPermissions', () => {
  it('keeps author/admin/report permissions in a single computed interface', () => {
    const post = ref(createPost())
    const currentUserId = ref<number | undefined>(7)
    const isAuthenticated = ref(true)
    const isAdmin = ref(false)
    const permissions = usePostDetailPermissions(post, {
      currentUserId,
      isAuthenticated,
      isAdmin
    })

    expect(permissions.isAuthor.value).toBe(true)
    expect(permissions.canEdit.value).toBe(true)
    expect(permissions.canDelete.value).toBe(true)
    expect(permissions.canReport.value).toBe(false)

    currentUserId.value = 8

    expect(permissions.isAuthor.value).toBe(false)
    expect(permissions.canEdit.value).toBe(false)
    expect(permissions.canDelete.value).toBe(false)
    expect(permissions.canReport.value).toBe(true)

    isAdmin.value = true
    expect(permissions.canDelete.value).toBe(true)
  })

  it('detects agent-authored posts for the header badge', () => {
    const post = ref(createPost({
      author: {
        userId: 9,
        displayName: 'Agent',
        authorType: 'AGENT'
      }
    }))

    const permissions = usePostDetailPermissions(post, {
      currentUserId: ref(7),
      isAuthenticated: ref(true),
      isAdmin: ref(false)
    })

    expect(permissions.isAgentAuthor.value).toBe(true)
  })
})
