import type { PostDraftData } from '@/api/post'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import type { DraftPost } from '@/types'
import logger from '@/utils/logger'
import { isCancellationError } from '@/utils/cancellationError'
import {
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'
import { stripDraftServerIdentity } from '@/features/board/posts/draft/postDraftSnapshot'

interface ResolveServerDraftOptions {
  payload: PostDraftData
  localSnapshot: DraftRecoverySnapshot | null
  preferredDraftId?: number | null
  signal?: AbortSignal
  generationIsCurrent: () => boolean
  onStaleLocalSnapshot: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
}

interface ResolveServerDraftResult {
  localSnapshot: DraftRecoverySnapshot | null
  serverDraft: DraftPost | null
  recoveryFailed: boolean
  draftProtected: boolean
  multipleMatchesFound: boolean
}

const isDraftRecoveryCancellation = (error: unknown) => isCancellationError(error, {
  names: ['AbortError', 'CanceledError'],
  codes: ['ERR_CANCELED'],
})

export async function resolveServerDraftForRecovery({
  payload,
  localSnapshot,
  preferredDraftId,
  signal,
  generationIsCurrent,
  onStaleLocalSnapshot,
}: ResolveServerDraftOptions): Promise<ResolveServerDraftResult> {
  let nextLocalSnapshot = localSnapshot
  let serverDraft: DraftPost | null = null
  let recoveryFailed = false
  let draftProtected = false
  let multipleMatchesFound = false
  const serverDraftId = preferredDraftId ?? nextLocalSnapshot?.draftId ?? null
  const requestConfig = { signal, skipGlobalErrorHandler: true }

  try {
    const resolution = unwrapAxiosApiData(await userApi.resolveDraftRecovery({
      boardUrl: payload.boardUrl,
      ...(payload.originalPostId != null ? { originalPostId: payload.originalPostId } : {}),
      ...(serverDraftId != null ? { draftId: serverDraftId } : {}),
      ...(nextLocalSnapshot?.clientDraftKey ?? payload.clientDraftKey
        ? { clientDraftKey: nextLocalSnapshot?.clientDraftKey ?? payload.clientDraftKey }
        : {}),
    }, requestConfig))
    if (!generationIsCurrent()) {
      return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
    }

    if (resolution.staleCandidate
      && nextLocalSnapshot?.draftId != null
      && nextLocalSnapshot.draftId === serverDraftId) {
      nextLocalSnapshot = onStaleLocalSnapshot(stripDraftServerIdentity(
        nextLocalSnapshot,
        nextLocalSnapshot.unassociatedUploadFileIds,
      ))
    }

    draftProtected = resolution.status === 'PROTECTED'
    multipleMatchesFound = resolution.status === 'AMBIGUOUS'
    if (resolution.status === 'AVAILABLE') {
      serverDraft = resolution.draft ?? null
      recoveryFailed = serverDraft == null
    }
  } catch (error: unknown) {
    if (!generationIsCurrent() || isDraftRecoveryCancellation(error)) {
      return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
    }
    logger.error('Failed to resolve server draft recovery:', error)
    recoveryFailed = true
  }

  return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
}
