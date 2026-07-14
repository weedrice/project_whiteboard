import { Storage } from '@/utils/storage'

const DRAFT_TOMBSTONE_PREFIX = 'noviis:draft-deleted'

function getDraftTombstoneKey(userId: string | number, draftId: string | number) {
  return `${DRAFT_TOMBSTONE_PREFIX}:${userId}:${draftId}`
}

export function markDraftDeletedLocally(userId: string | number, draftId: string | number) {
  Storage.set(getDraftTombstoneKey(userId, draftId), true)
}

export function isDraftDeletedLocally(
  userId: string | number | null | undefined,
  draftId: string | number | null | undefined,
) {
  if (userId == null || draftId == null) return false
  return Storage.get<boolean>(getDraftTombstoneKey(userId, draftId), false) === true
}
