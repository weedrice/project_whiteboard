import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import { getDraftUpdatedAt, type DraftRecoverySnapshot } from '@/composables/postDraftRecovery'

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
): DraftRecoverySnapshot {
  return {
    ...payload,
    draftId: savedDraft.draftId,
    updatedAt: getDraftUpdatedAt(savedDraft) ?? undefined,
  }
}

export function hasMeaningfulDraftContent(payload: PostDraftData): boolean {
  return Boolean(
    payload.title?.trim()
    || payload.contents?.trim()
    || payload.tags?.length
    || payload.fileIds?.length,
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
