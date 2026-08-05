import {
  parseDraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import type {
  DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

const DRAFT_UPDATED_CHANNEL = 'noviis-draft-updated'
const DRAFT_UPDATED_EVENT_KEY = 'noviis:draft-updated-event'
const DRAFT_UPDATED_EVENT_TTL_MS = 60_000
const MAX_FUTURE_EVENT_SKEW_MS = 5_000
const MAX_SEEN_EVENTS = 64

export interface DraftUpdatedEvent {
  type: 'draft-updated'
  eventId: string
  sourceId: string
  ownerId: string
  storageKey: string
  snapshot: DraftRecoverySnapshot
  at: number
}

type DraftUpdatedListener = (message: DraftUpdatedEvent) => void
type DraftUpdatedState = {
  sourceId: string
  channel: BroadcastChannel | null
  listeners: Set<DraftUpdatedListener>
  seenEventIds: Set<string>
  storageListenerInstalled: boolean
}

const GLOBAL_STATE_KEY = '__noviisDraftUpdatedChannel__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: DraftUpdatedState }
const state = globalState[GLOBAL_STATE_KEY] ??= {
  sourceId: createId(),
  channel: null,
  listeners: new Set(),
  seenEventIds: new Set(),
  storageListenerInstalled: false,
}

function createId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function parseDraftUpdatedEvent(value: unknown, now = Date.now()): DraftUpdatedEvent | null {
  if (!value || typeof value !== 'object') return null
  const message = value as Partial<DraftUpdatedEvent>
  const snapshot = parseDraftRecoverySnapshot(message.snapshot, now)
  if (message.type !== 'draft-updated'
    || typeof message.eventId !== 'string'
    || !message.eventId
    || message.eventId.length > 128
    || typeof message.sourceId !== 'string'
    || !message.sourceId
    || message.sourceId.length > 128
    || typeof message.ownerId !== 'string'
    || !message.ownerId
    || message.ownerId.length > 64
    || typeof message.storageKey !== 'string'
    || !message.storageKey
    || message.storageKey.length > 512
    || typeof message.at !== 'number'
    || !Number.isFinite(message.at)
    || message.at < now - DRAFT_UPDATED_EVENT_TTL_MS
    || message.at > now + MAX_FUTURE_EVENT_SKEW_MS
    || !snapshot) return null
  return { ...message, snapshot } as DraftUpdatedEvent
}

function rememberEvent(eventId: string): boolean {
  if (state.seenEventIds.has(eventId)) return false
  state.seenEventIds.add(eventId)
  if (state.seenEventIds.size > MAX_SEEN_EVENTS) {
    const oldest = state.seenEventIds.values().next().value
    if (typeof oldest === 'string') state.seenEventIds.delete(oldest)
  }
  return true
}

function receiveEvent(value: unknown) {
  const message = parseDraftUpdatedEvent(value)
  if (!message || message.sourceId === state.sourceId || !rememberEvent(message.eventId)) return
  state.listeners.forEach((listener) => listener(message))
}

function getChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') return null
  if (!state.channel) {
    try {
      state.channel = new BroadcastChannel(DRAFT_UPDATED_CHANNEL)
      state.channel.addEventListener('message', (event) => receiveEvent(event.data))
    } catch {
      state.channel = null
    }
  }
  return state.channel
}

function handleStorageEvent(event: StorageEvent) {
  if (event.key !== DRAFT_UPDATED_EVENT_KEY || !event.newValue) return
  try {
    receiveEvent(JSON.parse(event.newValue) as unknown)
  } catch {
    // Ignore malformed cross-tab events.
  }
}

function installStorageListener() {
  if (state.storageListenerInstalled || typeof window === 'undefined') return
  window.addEventListener('storage', handleStorageEvent)
  state.storageListenerInstalled = true
}

export function publishDraftUpdatedEvent(
  ownerId: string | number,
  storageKey: string,
  snapshot: DraftRecoverySnapshot,
) {
  const message: DraftUpdatedEvent = {
    type: 'draft-updated',
    eventId: createId(),
    sourceId: state.sourceId,
    ownerId: String(ownerId),
    storageKey,
    snapshot,
    at: Date.now(),
  }
  rememberEvent(message.eventId)
  try {
    getChannel()?.postMessage(message)
  } catch {
    // The storage event remains available when the channel is unavailable.
  }
  try {
    localStorage.setItem(DRAFT_UPDATED_EVENT_KEY, JSON.stringify(message))
    localStorage.removeItem(DRAFT_UPDATED_EVENT_KEY)
  } catch {
    // BroadcastChannel may still deliver when storage is blocked.
  }
}

export function registerDraftUpdatedListener(listener: DraftUpdatedListener): () => void {
  state.listeners.add(listener)
  getChannel()
  installStorageListener()
  return () => state.listeners.delete(listener)
}

export function closeDraftUpdatedChannelForTest() {
  state.channel?.close()
  state.channel = null
  state.listeners.clear()
  state.seenEventIds.clear()
  if (state.storageListenerInstalled && typeof window !== 'undefined') {
    window.removeEventListener('storage', handleStorageEvent)
    state.storageListenerInstalled = false
  }
  try {
    localStorage.removeItem(DRAFT_UPDATED_EVENT_KEY)
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
}
