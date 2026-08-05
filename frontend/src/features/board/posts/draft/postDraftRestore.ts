import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import logger from '@/utils/logger'
import { isCancellationError } from '@/utils/cancellationError'
import {
  isDraftMissingError,
  isDraftProtectedError,
  isMatchingLoadedDraft,
  loadDraftById,
  resolveMatchingServerDraft,
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

export interface ResolveServerDraftResult {
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
  let serverDraftId = preferredDraftId ?? nextLocalSnapshot?.draftId ?? null
  const requestConfig = { signal, skipGlobalErrorHandler: true }
  const resolveMatchingDraft = () => resolveMatchingServerDraft({
    ...payload,
    clientDraftKey: nextLocalSnapshot?.clientDraftKey ?? payload.clientDraftKey,
  }, requestConfig)

  if (serverDraftId == null) {
    try {
      const matchingDraft = await resolveMatchingDraft()
      serverDraftId = matchingDraft.draftId
      multipleMatchesFound = matchingDraft.multipleMatchesFound
    } catch (error: unknown) {
      if (!generationIsCurrent() || isDraftRecoveryCancellation(error)) {
        return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
      }
      logger.error('Failed to resolve server draft id:', error)
      recoveryFailed = true
    }
  }

  if (serverDraftId == null) {
    return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
  }

  try {
    if (!generationIsCurrent()) {
      return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
    }
    const loadedDraft = await loadDraftById(serverDraftId, requestConfig)
    if (isMatchingLoadedDraft(loadedDraft, payload)) {
      serverDraft = loadedDraft
    } else {
      const fallbackResolution = await resolveMatchingDraft()
      const fallbackDraftId = fallbackResolution.draftId
      multipleMatchesFound ||= fallbackResolution.multipleMatchesFound
      if (fallbackDraftId != null && fallbackDraftId !== serverDraftId) {
        const fallbackDraft = await loadDraftById(fallbackDraftId, requestConfig)
        if (isMatchingLoadedDraft(fallbackDraft, payload)) {
          serverDraft = fallbackDraft
        }
      }
    }
  } catch (error: unknown) {
    if (!generationIsCurrent() || isDraftRecoveryCancellation(error)) {
      return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
    }
    if (isDraftProtectedError(error)) {
      draftProtected = true
    } else if (
      nextLocalSnapshot?.draftId === serverDraftId
      && isDraftMissingError(error)
    ) {
      nextLocalSnapshot = onStaleLocalSnapshot(stripDraftServerIdentity(
        nextLocalSnapshot,
        nextLocalSnapshot.unassociatedUploadFileIds,
      ))
      try {
        const fallbackResolution = await resolveMatchingDraft()
        const fallbackDraftId = fallbackResolution.draftId
        multipleMatchesFound ||= fallbackResolution.multipleMatchesFound
        if (fallbackDraftId != null) {
          serverDraft = await loadDraftById(fallbackDraftId, requestConfig)
        }
      } catch (resolveError: unknown) {
        if (!generationIsCurrent() || isDraftRecoveryCancellation(resolveError)) {
          return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
        }
        if (isDraftProtectedError(resolveError)) draftProtected = true
        else if (!isDraftMissingError(resolveError)) {
          logger.error('Failed to restore replacement server draft:', resolveError)
          recoveryFailed = true
        }
      }
    } else {
      logger.error('Failed to restore server draft:', error)
      recoveryFailed = true
    }
  }

  return { localSnapshot: nextLocalSnapshot, serverDraft, recoveryFailed, draftProtected, multipleMatchesFound }
}
