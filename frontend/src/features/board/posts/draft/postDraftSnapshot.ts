import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import { getDraftUpdatedAt, type DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import { removePostFileReferencesFromContent } from '@/utils/postForm'

export function createDraftRecoverySnapshot(
  payload: PostDraftData,
  draftId: number | null,
  updatedAt: string | null,
  clientModifiedAt = new Date().toISOString(),
): DraftRecoverySnapshot {
  return {
    ...payload,
    draftId: draftId ?? undefined,
    updatedAt: updatedAt ?? undefined,
    clientModifiedAt,
    hasLocalChanges: true,
  }
}

export function createStoredSavedDraftSnapshot(
  payload: PostDraftData,
  savedDraft: DraftPost,
  fallbackUpdatedAt?: string,
  clientModifiedAt = new Date().toISOString(),
): DraftRecoverySnapshot {
  return {
    ...payload,
    ...savedDraft,
    clientDraftKey: savedDraft.clientDraftKey ?? payload.clientDraftKey,
    originalPostId: savedDraft.originalPostId ?? payload.originalPostId,
    contents: savedDraft.contents ?? payload.contents,
    title: savedDraft.title ?? payload.title,
    poll: savedDraft.poll ?? null,
    seriesId: savedDraft.seriesId ?? undefined,
    draftId: savedDraft.draftId,
    updatedAt: getDraftUpdatedAt(savedDraft) ?? fallbackUpdatedAt,
    clientModifiedAt,
    hasLocalChanges: false,
  }
}

export function hasMeaningfulDraftContent(payload: PostDraftData): boolean {
  return Boolean(
    payload.title?.trim()
    || payload.contents?.trim()
    || payload.tags?.length
    || payload.fileIds?.length
    || payload.isNotice
    || payload.isNsfw
    || payload.isSpoiler
    || payload.isSecret
    || payload.poll
    || payload.seriesId != null,
  )
}

export function hasBrowserDraftContent(payload: PostDraftData): boolean {
  return Boolean(
    hasMeaningfulDraftContent(payload)
    || payload.categoryId != null,
  )
}

export function stripDraftServerIdentity(snapshot: DraftRecoverySnapshot): DraftRecoverySnapshot {
  return {
    ...snapshot,
    draftId: undefined,
    clientDraftKey: undefined,
    version: undefined,
    updatedAt: undefined,
    modifiedAt: undefined,
    contents: snapshot.contents == null
      ? snapshot.contents
      : removePostFileReferencesFromContent(snapshot.contents, snapshot.fileIds ?? []),
    fileIds: [],
    staleReferencesReset: true,
    hasLocalChanges: true,
    clientModifiedAt: new Date().toISOString(),
  }
}
