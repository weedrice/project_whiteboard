import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import { getDraftUpdatedAt, type DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

export function createDraftRecoverySnapshot(
  payload: PostDraftData,
  draftId: number | null,
  updatedAt: string | null,
): DraftRecoverySnapshot {
  return {
    ...payload,
    draftId: draftId ?? undefined,
    updatedAt: updatedAt ?? undefined,
  }
}

export function createStoredSavedDraftSnapshot(
  payload: PostDraftData,
  savedDraft: DraftPost,
  fallbackUpdatedAt?: string,
): DraftRecoverySnapshot {
  return {
    ...payload,
    poll: savedDraft.poll ?? null,
    seriesId: savedDraft.seriesId ?? undefined,
    draftId: savedDraft.draftId,
    updatedAt: getDraftUpdatedAt(savedDraft) ?? fallbackUpdatedAt,
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
    updatedAt: undefined,
    modifiedAt: undefined,
  }
}
