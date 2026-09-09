import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PostDraftData } from '@/api/post'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

const mocks = vi.hoisted(() => ({
  resolveDraftRecovery: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userApi: { resolveDraftRecovery: mocks.resolveDraftRecovery },
}))

const payload: PostDraftData = {
  boardUrl: 'free',
  originalPostId: 7,
  title: '',
  contents: '',
  fileIds: [],
}

const draft = {
  draftId: 13,
  boardId: 1,
  boardUrl: 'free',
  boardName: 'Free',
  originalPostId: 7,
  title: 'Recovered',
  contents: 'Body',
  fileIds: [],
  tags: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  updatedAt: '2026-09-09T00:00:00.000Z',
}

describe('server draft recovery resolver', () => {
  beforeEach(() => vi.clearAllMocks())

  it('resolves the candidate and fallback in one server call', async () => {
    mocks.resolveDraftRecovery.mockResolvedValue({
      data: { data: { status: 'AVAILABLE', staleCandidate: true, draftId: 13, draft } },
    })
    const staleSnapshot: DraftRecoverySnapshot = {
      ...payload,
      title: 'Local',
      draftId: 91,
      clientDraftKey: 'local-client-key',
      version: 4,
    }
    const onStaleLocalSnapshot = vi.fn((snapshot: DraftRecoverySnapshot) => snapshot)

    const resolved = await resolveServerDraftForRecovery({
      payload,
      localSnapshot: staleSnapshot,
      signal: new AbortController().signal,
      generationIsCurrent: () => true,
      onStaleLocalSnapshot,
    })

    expect(mocks.resolveDraftRecovery).toHaveBeenCalledExactlyOnceWith({
      boardUrl: 'free',
      originalPostId: 7,
      draftId: 91,
      clientDraftKey: 'local-client-key',
    }, expect.objectContaining({ signal: expect.any(AbortSignal), skipGlobalErrorHandler: true }))
    expect(onStaleLocalSnapshot).toHaveBeenCalledWith(expect.not.objectContaining({ draftId: 91 }))
    expect(resolved).toMatchObject({
      serverDraft: draft,
      recoveryFailed: false,
      draftProtected: false,
      multipleMatchesFound: false,
    })
  })

  it.each([
    ['PROTECTED', true, false],
    ['AMBIGUOUS', false, true],
    ['MISSING', false, false],
  ] as const)('maps %s without a follow-up fetch', async (status, draftProtected, multipleMatchesFound) => {
    mocks.resolveDraftRecovery.mockResolvedValue({
      data: { data: { status, staleCandidate: false } },
    })

    const resolved = await resolveServerDraftForRecovery({
      payload,
      localSnapshot: null,
      generationIsCurrent: () => true,
      onStaleLocalSnapshot: vi.fn(),
    })

    expect(mocks.resolveDraftRecovery).toHaveBeenCalledOnce()
    expect(resolved).toMatchObject({
      serverDraft: null,
      recoveryFailed: false,
      draftProtected,
      multipleMatchesFound,
    })
  })

  it('does not report cancellation from an invalidated generation as a failure', async () => {
    mocks.resolveDraftRecovery.mockRejectedValue({ name: 'CanceledError', code: 'ERR_CANCELED' })

    const resolved = await resolveServerDraftForRecovery({
      payload,
      localSnapshot: null,
      generationIsCurrent: () => false,
      onStaleLocalSnapshot: vi.fn(),
    })

    expect(resolved.recoveryFailed).toBe(false)
  })
})
