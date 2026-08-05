const DEFAULT_EVENT_TTL_MS = 60_000
const DEFAULT_MAX_FUTURE_EVENT_SKEW_MS = 5_000
const DEFAULT_MAX_SEEN_EVENTS = 64

export interface DraftCrossTabEventBase {
  eventId: string
  sourceId: string
  at: number
}

type DraftCrossTabListener<TEvent> = (message: TEvent) => void

type DraftCrossTabState<TEvent> = {
  sourceId: string
  channel: BroadcastChannel | null
  listeners: Set<DraftCrossTabListener<TEvent>>
  seenEventIds: Set<string>
  storageListenerInstalled: boolean
}

interface DraftCrossTabChannelOptions<TEvent extends DraftCrossTabEventBase> {
  channelName: string
  storageKey: string
  globalStateKey: string
  parseEvent: (value: unknown) => TEvent | null
  maxSeenEvents?: number
}

function createId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function createState<TEvent>(): DraftCrossTabState<TEvent> {
  return {
    sourceId: createId(),
    channel: null,
    listeners: new Set(),
    seenEventIds: new Set(),
    storageListenerInstalled: false,
  }
}

export function isValidDraftCrossTabEventEnvelope(
  value: unknown,
  expectedType: string,
  now = Date.now(),
  ttlMs = DEFAULT_EVENT_TTL_MS,
  maxFutureSkewMs = DEFAULT_MAX_FUTURE_EVENT_SKEW_MS,
): value is DraftCrossTabEventBase & { type: string } {
  if (!value || typeof value !== 'object') return false
  const message = value as Partial<DraftCrossTabEventBase & { type: string }>
  return message.type === expectedType
    && typeof message.eventId === 'string'
    && message.eventId.length > 0
    && message.eventId.length <= 128
    && typeof message.sourceId === 'string'
    && message.sourceId.length > 0
    && message.sourceId.length <= 128
    && typeof message.at === 'number'
    && Number.isFinite(message.at)
    && message.at >= now - ttlMs
    && message.at <= now + maxFutureSkewMs
}

export function createDraftCrossTabChannel<TEvent extends DraftCrossTabEventBase>({
  channelName,
  storageKey,
  globalStateKey,
  parseEvent,
  maxSeenEvents = DEFAULT_MAX_SEEN_EVENTS,
}: DraftCrossTabChannelOptions<TEvent>) {
  const globalState = globalThis as typeof globalThis & Record<string, unknown>
  const existingState = globalState[globalStateKey] as DraftCrossTabState<TEvent> | undefined
  const state = existingState ?? createState<TEvent>()
  if (!existingState) globalState[globalStateKey] = state

  const rememberEvent = (eventId: string): boolean => {
    if (state.seenEventIds.has(eventId)) return false
    state.seenEventIds.add(eventId)
    if (state.seenEventIds.size > maxSeenEvents) {
      const oldest = state.seenEventIds.values().next().value
      if (typeof oldest === 'string') state.seenEventIds.delete(oldest)
    }
    return true
  }

  const receiveParsedEvent = (message: TEvent | null) => {
    if (!message || message.sourceId === state.sourceId || !rememberEvent(message.eventId)) return
    state.listeners.forEach((listener) => listener(message))
  }

  const receiveEvent = (value: unknown) => receiveParsedEvent(parseEvent(value))

  const getChannel = (): BroadcastChannel | null => {
    if (typeof BroadcastChannel === 'undefined') return null
    if (!state.channel) {
      try {
        state.channel = new BroadcastChannel(channelName)
        state.channel.addEventListener('message', (event) => receiveEvent(event.data))
      } catch {
        state.channel = null
      }
    }
    return state.channel
  }

  const parseStorageEvent = (event: StorageEvent): TEvent | null => {
    if (event.key !== storageKey || !event.newValue) return null
    try {
      return parseEvent(JSON.parse(event.newValue) as unknown)
    } catch {
      return null
    }
  }

  const handleStorageEvent = (event: StorageEvent) => {
    receiveParsedEvent(parseStorageEvent(event))
  }

  const installStorageListener = () => {
    if (state.storageListenerInstalled || typeof window === 'undefined') return
    window.addEventListener('storage', handleStorageEvent)
    state.storageListenerInstalled = true
  }

  const publish = (payload: Omit<TEvent, keyof DraftCrossTabEventBase>): TEvent => {
    const message = {
      ...payload,
      eventId: createId(),
      sourceId: state.sourceId,
      at: Date.now(),
    } as unknown as TEvent
    rememberEvent(message.eventId)
    try {
      getChannel()?.postMessage(message)
    } catch {
      // The storage event remains available when the channel is unavailable.
    }
    try {
      localStorage.setItem(storageKey, JSON.stringify(message))
      localStorage.removeItem(storageKey)
    } catch {
      // BroadcastChannel may still deliver when storage is blocked.
    }
    return message
  }

  const register = (listener: DraftCrossTabListener<TEvent>): (() => void) => {
    state.listeners.add(listener)
    getChannel()
    installStorageListener()
    return () => state.listeners.delete(listener)
  }

  const closeForTest = () => {
    state.channel?.close()
    state.channel = null
    state.listeners.clear()
    state.seenEventIds.clear()
    if (state.storageListenerInstalled && typeof window !== 'undefined') {
      window.removeEventListener('storage', handleStorageEvent)
      state.storageListenerInstalled = false
    }
    try {
      localStorage.removeItem(storageKey)
    } catch {
      // Ignore unavailable storage in non-browser tests.
    }
  }

  return {
    publish,
    register,
    parseStorageEvent,
    closeForTest,
  }
}
