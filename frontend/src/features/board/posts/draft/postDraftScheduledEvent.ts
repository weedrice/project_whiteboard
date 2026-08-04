const DRAFT_SCHEDULED_CHANNEL = 'noviis-draft-scheduled'
const DRAFT_SCHEDULED_EVENT_KEY = 'noviis:draft-scheduled-event'
const DRAFT_SCHEDULED_EVENT_TTL_MS = 60_000
const MAX_FUTURE_EVENT_SKEW_MS = 5_000
const MAX_SEEN_EVENTS = 64

export interface DraftScheduledEvent {
  type: 'draft-scheduled'
  eventId: string
  sourceId: string
  ownerId: string
  draftId: number | null
  clientDraftKey: string | null
  storageKey: string
  at: number
}

type DraftScheduledListener = (message: DraftScheduledEvent) => void

type DraftScheduledState = {
  sourceId: string
  channel: BroadcastChannel | null
  listeners: Set<DraftScheduledListener>
  seenEventIds: Set<string>
  storageListenerInstalled: boolean
}

const GLOBAL_STATE_KEY = '__noviisDraftScheduledChannel__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: DraftScheduledState }
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

function isDraftScheduledEvent(value: unknown, now = Date.now()): value is DraftScheduledEvent {
  if (!value || typeof value !== 'object') return false
  const message = value as Partial<DraftScheduledEvent>
  return message.type === 'draft-scheduled'
    && typeof message.eventId === 'string'
    && message.eventId.length > 0
    && message.eventId.length <= 128
    && typeof message.sourceId === 'string'
    && message.sourceId.length > 0
    && message.sourceId.length <= 128
    && typeof message.ownerId === 'string'
    && message.ownerId.length > 0
    && message.ownerId.length <= 64
    && (message.draftId === null
      || (typeof message.draftId === 'number' && Number.isInteger(message.draftId) && message.draftId > 0))
    && (message.clientDraftKey === null
      || (typeof message.clientDraftKey === 'string'
        && /^[A-Za-z0-9_-]{8,64}$/.test(message.clientDraftKey)))
    && typeof message.storageKey === 'string'
    && message.storageKey.length > 0
    && message.storageKey.length <= 512
    && typeof message.at === 'number'
    && Number.isFinite(message.at)
    && message.at >= now - DRAFT_SCHEDULED_EVENT_TTL_MS
    && message.at <= now + MAX_FUTURE_EVENT_SKEW_MS
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
  if (!isDraftScheduledEvent(value)
    || value.sourceId === state.sourceId
    || !rememberEvent(value.eventId)) return
  state.listeners.forEach((listener) => listener(value))
}

function getChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') return null
  if (!state.channel) {
    try {
      state.channel = new BroadcastChannel(DRAFT_SCHEDULED_CHANNEL)
      state.channel.addEventListener('message', (event) => receiveEvent(event.data))
    } catch {
      state.channel = null
    }
  }
  return state.channel
}

function handleStorageEvent(event: StorageEvent) {
  const message = parseDraftScheduledStorageEvent(event)
  if (message) receiveEvent(message)
}

function installStorageListener() {
  if (state.storageListenerInstalled || typeof window === 'undefined') return
  window.addEventListener('storage', handleStorageEvent)
  state.storageListenerInstalled = true
}

export function publishDraftScheduledEvent(
  ownerId: string | number,
  draftId: number | null,
  clientDraftKey: string | null,
  storageKey: string,
) {
  const message: DraftScheduledEvent = {
    type: 'draft-scheduled',
    eventId: createId(),
    sourceId: state.sourceId,
    ownerId: String(ownerId),
    draftId,
    clientDraftKey,
    storageKey,
    at: Date.now(),
  }
  rememberEvent(message.eventId)
  try {
    getChannel()?.postMessage(message)
  } catch {
    // The storage event remains available as a fallback.
  }
  try {
    localStorage.setItem(DRAFT_SCHEDULED_EVENT_KEY, JSON.stringify(message))
    localStorage.removeItem(DRAFT_SCHEDULED_EVENT_KEY)
  } catch {
    // BroadcastChannel may still deliver when storage is blocked.
  }
}

export function parseDraftScheduledStorageEvent(event: StorageEvent): DraftScheduledEvent | null {
  if (event.key !== DRAFT_SCHEDULED_EVENT_KEY || !event.newValue) return null
  try {
    const value = JSON.parse(event.newValue) as unknown
    return isDraftScheduledEvent(value) ? value : null
  } catch {
    return null
  }
}

export function matchesDraftScheduledEvent(
  message: DraftScheduledEvent,
  ownerId: string | number | null | undefined,
  draftId: number | null,
  clientDraftKey: string | null,
  storageKey: string,
) {
  if (ownerId == null || message.ownerId !== String(ownerId)) return false
  if (message.draftId != null && message.draftId === draftId) return true
  if (message.clientDraftKey != null && clientDraftKey != null) {
    return message.clientDraftKey === clientDraftKey
  }
  return message.storageKey === storageKey
}

export function registerDraftScheduledListener(listener: DraftScheduledListener): () => void {
  state.listeners.add(listener)
  getChannel()
  installStorageListener()
  return () => state.listeners.delete(listener)
}

export function closeDraftScheduledChannelForTest() {
  state.channel?.close()
  state.channel = null
  state.listeners.clear()
  state.seenEventIds.clear()
  if (state.storageListenerInstalled && typeof window !== 'undefined') {
    window.removeEventListener('storage', handleStorageEvent)
    state.storageListenerInstalled = false
  }
  try {
    localStorage.removeItem(DRAFT_SCHEDULED_EVENT_KEY)
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
}
