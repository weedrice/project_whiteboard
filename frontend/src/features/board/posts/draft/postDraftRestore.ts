import { isAxiosError } from 'axios'
import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import logger from '@/utils/logger'
import {
  findMatchingServerDraftId,
  loadDraftById,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'
import { stripDraftServerIdentity } from '@/features/board/posts/draft/postDraftSnapshot'

interface ResolveServerDraftOptions {
  payload: PostDraftData
  localSnapshot: DraftRecoverySnapshot | null
  generationIsCurrent: () => boolean
  onStaleLocalSnapshot: (snapshot: DraftRecoverySnapshot) => void
}

export interface ResolveServerDraftResult {
  localSnapshot: DraftRecoverySnapshot | null
  serverDraft: DraftPost | null
}

export async function resolveServerDraftForRecovery({
  payload,
  localSnapshot,
  generationIsCurrent,
  onStaleLocalSnapshot,
}: ResolveServerDraftOptions): Promise<ResolveServerDraftResult> {
  let nextLocalSnapshot = localSnapshot
  let serverDraft: DraftPost | null = null
  let serverDraftId = nextLocalSnapshot?.draftId ?? null

  if (serverDraftId == null) {
    try {
      serverDraftId = await findMatchingServerDraftId(payload)
    } catch (error: unknown) {
      logger.error('Failed to resolve server draft id:', error)
    }
  }

  if (serverDraftId == null) {
    return { localSnapshot: nextLocalSnapshot, serverDraft }
  }

  try {
    if (!generationIsCurrent()) return { localSnapshot: nextLocalSnapshot, serverDraft }
    serverDraft = await loadDraftById(serverDraftId)
  } catch (error: unknown) {
    if (!generationIsCurrent()) return { localSnapshot: nextLocalSnapshot, serverDraft }
    if (
      nextLocalSnapshot?.draftId === serverDraftId
      && isAxiosError(error)
      && error.response?.status === 404
    ) {
      nextLocalSnapshot = stripDraftServerIdentity(nextLocalSnapshot)
      onStaleLocalSnapshot(nextLocalSnapshot)
      serverDraftId = null
      try {
        const fallbackDraftId = await findMatchingServerDraftId(payload)
        if (fallbackDraftId != null) {
          serverDraft = await loadDraftById(fallbackDraftId)
        }
      } catch (resolveError: unknown) {
        logger.error('Failed to restore replacement server draft:', resolveError)
      }
    }
    logger.error('Failed to restore server draft:', error)
  }

  return { localSnapshot: nextLocalSnapshot, serverDraft }
}
