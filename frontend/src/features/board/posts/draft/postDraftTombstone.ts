import { Storage } from '@/utils/storage'

const DRAFT_TOMBSTONE_PREFIX = 'noviis:draft-deleted'
const TOMBSTONE_RETENTION_MS = 90 * 24 * 60 * 60 * 1000
const DRAFT_DELETED_CHANNEL = 'noviis-draft-deleted'

type DraftTombstone = {
  deletedAt: string
}

export type DraftDeletedEvent = {
  type: 'draft-deleted'
  sourceId: string
  ownerId: string
  draftId: string
  at: number
}

type DraftDeletedListener = (event: DraftDeletedEvent) => void
type DraftDeletedState = {
  sourceId: string
  channel: BroadcastChannel | null
  listeners: Set<DraftDeletedListener>
}

const GLOBAL_STATE_KEY = '__noviisDraftDeletedChannel__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: DraftDeletedState }
const state = globalState[GLOBAL_STATE_KEY] ??= {
  sourceId: typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  channel: null,
  listeners: new Set(),
}

function isDraftDeletedEvent(value: unknown): value is DraftDeletedEvent {
  if (!value || typeof value !== 'object') return false
  const event = value as Partial<DraftDeletedEvent>
  return event.type === 'draft-deleted'
    && typeof event.sourceId === 'string'
    && event.sourceId.length > 0
    && typeof event.ownerId === 'string'
    && event.ownerId.length > 0
    && typeof event.draftId === 'string'
    && event.draftId.length > 0
    && typeof event.at === 'number'
    && Number.isFinite(event.at)
}

function receiveDeletedEvent(value: unknown) {
  if (!isDraftDeletedEvent(value) || value.sourceId === state.sourceId) return
  state.listeners.forEach((listener) => listener(value))
}

function getDeletedChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') return null
  if (!state.channel) {
    try {
      state.channel = new BroadcastChannel(DRAFT_DELETED_CHANNEL)
      state.channel.addEventListener('message', (event) => receiveDeletedEvent(event.data))
    } catch {
      state.channel = null
    }
  }
  return state.channel
}

export function getDraftTombstoneKey(userId: string | number, draftId: string | number) {
  return `${DRAFT_TOMBSTONE_PREFIX}:${userId}:${draftId}`
}

export function markDraftDeletedLocally(userId: string | number, draftId: string | number) {
  const at = Date.now()
  const stored = Storage.set(getDraftTombstoneKey(userId, draftId), { deletedAt: new Date(at).toISOString() })
  try {
    getDeletedChannel()?.postMessage({
      type: 'draft-deleted',
      sourceId: state.sourceId,
      ownerId: String(userId),
      draftId: String(draftId),
      at,
    } satisfies DraftDeletedEvent)
  } catch {
    // The tombstone storage event remains available when the channel is unavailable.
  }
  return stored
}

export function registerDraftDeletedListener(listener: DraftDeletedListener): () => void {
  state.listeners.add(listener)
  getDeletedChannel()
  return () => state.listeners.delete(listener)
}

export function closeDraftDeletedChannelForTest() {
  state.channel?.close()
  state.channel = null
  state.listeners.clear()
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

export function clearDraftTombstonesForUser(userId: string | number) {
  const userPrefix = `${DRAFT_TOMBSTONE_PREFIX}:${userId}:`
  let removed = 0
  for (const key of Storage.keys()) {
    if (!key.startsWith(userPrefix)) continue
    if (Storage.remove(key)) removed++
  }
  return removed
}
