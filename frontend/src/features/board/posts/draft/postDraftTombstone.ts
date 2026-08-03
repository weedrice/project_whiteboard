import { Storage } from '@/utils/storage'

const DRAFT_TOMBSTONE_PREFIX = 'noviis:draft-deleted'
const TOMBSTONE_RETENTION_MS = 90 * 24 * 60 * 60 * 1000

type DraftTombstone = {
  deletedAt: string
}

function getDraftTombstoneKey(userId: string | number, draftId: string | number) {
  return `${DRAFT_TOMBSTONE_PREFIX}:${userId}:${draftId}`
}

export function markDraftDeletedLocally(userId: string | number, draftId: string | number) {
  Storage.set(getDraftTombstoneKey(userId, draftId), { deletedAt: new Date().toISOString() })
}

export function isDraftDeletedLocally(
  userId: string | number | null | undefined,
  draftId: string | number | null | undefined,
) {
  if (userId == null || draftId == null) return false
  const key = getDraftTombstoneKey(userId, draftId)
  const tombstone = Storage.get<DraftTombstone | boolean>(key, false)
  if (tombstone === true) {
    Storage.set(key, { deletedAt: new Date().toISOString() })
    return true
  }
  if (!tombstone || !tombstone.deletedAt) return false
  const deletedAt = Date.parse(tombstone.deletedAt)
  if (Number.isFinite(deletedAt) && deletedAt < Date.now() - TOMBSTONE_RETENTION_MS) {
    Storage.remove(key)
    return false
  }
  return true
}

export function cleanupExpiredDraftTombstones() {
  for (const key of Storage.keys()) {
    if (!key.startsWith(`${DRAFT_TOMBSTONE_PREFIX}:`)) continue
    const parts = key.split(':')
    const userId = parts.at(-2)
    const draftId = parts.at(-1)
    if (userId && draftId) isDraftDeletedLocally(userId, draftId)
  }
}
