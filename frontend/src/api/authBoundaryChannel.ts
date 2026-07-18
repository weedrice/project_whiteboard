const AUTH_BOUNDARY_CHANNEL = 'noviis-auth-boundary'
const AUTH_BOUNDARY_STORAGE_KEY = 'noviisAuthBoundaryEvent'
const MAX_SEEN_BOUNDARIES = 64

export type AuthBoundaryKind = 'login' | 'logout' | 'account-switch'

export interface AuthBoundaryMessage {
  type: 'auth-boundary'
  boundary: AuthBoundaryKind
  eventId: string
  sourceId: string
  at: number
}
type AuthBoundaryListener = (message: AuthBoundaryMessage) => void

type AuthBoundaryState = {
  sourceId: string
  channel: BroadcastChannel | null
  listeners: Set<AuthBoundaryListener>
  seenEventIds: Set<string>
  storageListenerInstalled: boolean
}

const GLOBAL_STATE_KEY = '__noviisAuthBoundaryChannel__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: AuthBoundaryState }
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

function isAuthBoundaryMessage(value: unknown): value is AuthBoundaryMessage {
  if (!value || typeof value !== 'object') return false
  const message = value as Partial<AuthBoundaryMessage>
  return message.type === 'auth-boundary'
    && (message.boundary === 'login' || message.boundary === 'logout' || message.boundary === 'account-switch')
    && typeof message.eventId === 'string'
    && typeof message.sourceId === 'string'
    && typeof message.at === 'number'
}

function rememberBoundary(eventId: string): boolean {
  if (state.seenEventIds.has(eventId)) return false
  state.seenEventIds.add(eventId)
  if (state.seenEventIds.size > MAX_SEEN_BOUNDARIES) {
    const oldest = state.seenEventIds.values().next().value
    if (typeof oldest === 'string') state.seenEventIds.delete(oldest)
  }
  return true
}

function receiveBoundary(value: unknown) {
  if (!isAuthBoundaryMessage(value) || value.sourceId === state.sourceId || !rememberBoundary(value.eventId)) return
  state.listeners.forEach((listener) => listener(value))
}

function getChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') return null
  if (!state.channel) {
    state.channel = new BroadcastChannel(AUTH_BOUNDARY_CHANNEL)
    state.channel.addEventListener('message', (event) => receiveBoundary(event.data))
  }
  return state.channel
}

function handleStorageEvent(event: StorageEvent) {
  if (event.key !== AUTH_BOUNDARY_STORAGE_KEY || !event.newValue) return
  try {
    receiveBoundary(JSON.parse(event.newValue) as unknown)
  } catch {
    // Ignore malformed cross-tab messages.
  }
}

function installStorageListener() {
  if (state.storageListenerInstalled || typeof window === 'undefined') return
  window.addEventListener('storage', handleStorageEvent)
  state.storageListenerInstalled = true
}

export function publishAuthBoundary(boundary: AuthBoundaryKind): void {
  const message: AuthBoundaryMessage = {
    type: 'auth-boundary',
    boundary,
    eventId: createId(),
    sourceId: state.sourceId,
    at: Date.now(),
  }
  rememberBoundary(message.eventId)
  getChannel()?.postMessage(message)
  try {
    localStorage.setItem(AUTH_BOUNDARY_STORAGE_KEY, JSON.stringify(message))
    localStorage.removeItem(AUTH_BOUNDARY_STORAGE_KEY)
  } catch {
    // BroadcastChannel may still deliver the boundary when storage is blocked.
  }
}

export function registerAuthBoundaryListener(listener: AuthBoundaryListener): () => void {
  state.listeners.add(listener)
  getChannel()
  installStorageListener()
  return () => state.listeners.delete(listener)
}

export function closeAuthBoundaryChannelForTest(): void {
  state.channel?.close()
  state.channel = null
  state.listeners.clear()
  state.seenEventIds.clear()
  if (state.storageListenerInstalled && typeof window !== 'undefined') {
    window.removeEventListener('storage', handleStorageEvent)
    state.storageListenerInstalled = false
  }
  try {
    localStorage.removeItem(AUTH_BOUNDARY_STORAGE_KEY)
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
}
