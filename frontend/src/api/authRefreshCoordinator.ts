const AUTH_REFRESH_LOCK = 'noviis-auth-refresh'
const AUTH_REFRESH_CHANNEL = 'noviis-auth-session'
const AUTH_REFRESH_STORAGE_EVENT_KEY = 'noviisAuthRefreshEvent'
const AUTH_REFRESH_STORAGE_LEASE_KEY = 'noviisAuthRefreshLease'
const ELECTION_WINDOW_MS = 40
const PEER_RESULT_TIMEOUT_MS = 15_000
const STORAGE_LEASE_MS = 20_000

type LockManagerLike = {
  request: <T>(name: string, callback: () => Promise<T>) => Promise<T>
}

type RefreshMessage = {
  type: 'refresh-request' | 'refresh-result' | 'refresh-error' | 'refresh-cancelled'
  sourceId: string
  requestId?: string
  previousToken?: string | null
  accessToken?: string
  message?: string
  at: number
}

type RefreshFlight = {
  promise: Promise<string>
  controller: AbortController
  epoch: number
  previousToken: string | null
}

type PendingResult = {
  previousToken: string | null
  resolve: (token: string) => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}

type StorageWaiter = {
  previousToken: string | null
  resolve: (message: RefreshMessage) => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}

type CoordinatorState = {
  sourceId: string
  epoch: number
  channel: BroadcastChannel | null
  inFlight: RefreshFlight | null
  latestResult: RefreshMessage | null
  candidates: Map<string, RefreshMessage>
  pendingResults: Set<PendingResult>
  storageWaiters: Set<StorageWaiter>
  storageListenerInstalled: boolean
}

export interface CoordinateAuthRefreshOptions {
  previousToken?: string | null
  signal?: AbortSignal
}

const GLOBAL_STATE_KEY = '__noviisAuthRefreshCoordinator__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: CoordinatorState }
const state = globalState[GLOBAL_STATE_KEY] ??= {
  sourceId: createId(),
  epoch: 0,
  channel: null,
  inFlight: null,
  latestResult: null,
  candidates: new Map(),
  pendingResults: new Set(),
  storageWaiters: new Set(),
  storageListenerInstalled: false,
}

function createId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function delay(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      clearTimeout(timer)
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
    }, { once: true })
  })
}

function isRefreshMessage(value: unknown): value is RefreshMessage {
  if (!value || typeof value !== 'object') return false
  const type = (value as { type?: unknown }).type
  return type === 'refresh-request' || type === 'refresh-result'
    || type === 'refresh-error' || type === 'refresh-cancelled'
}

function notifyPendingResults(message: RefreshMessage) {
  if (message.type !== 'refresh-result' && message.type !== 'refresh-error') return
  for (const pending of state.pendingResults) {
    if (pending.previousToken !== (message.previousToken ?? null)) continue
    clearTimeout(pending.timer)
    state.pendingResults.delete(pending)
    if (message.type === 'refresh-result' && message.accessToken) pending.resolve(message.accessToken)
    else pending.reject(new Error(message.message || 'Authentication refresh failed'))
  }
}

function handleMessage(value: unknown) {
  if (!isRefreshMessage(value) || value.sourceId === state.sourceId) return
  if (value.type === 'refresh-request' && value.requestId) {
    state.candidates.set(value.requestId, value)
    const latest = state.latestResult
    if (latest?.type === 'refresh-result'
      && latest.previousToken === value.previousToken
      && Date.now() - latest.at < 5000) {
      postChannel({ ...latest, requestId: value.requestId })
    }
    return
  }
  if (value.type === 'refresh-cancelled') {
    cancelLocalAuthRefresh()
    return
  }
  if (value.type === 'refresh-result') state.latestResult = value
  notifyPendingResults(value)
}

function cancelLocalAuthRefresh() {
  state.epoch += 1
  state.latestResult = null
  state.candidates.clear()
  state.inFlight?.controller.abort()
  const error = new DOMException('Authentication refresh was cancelled', 'AbortError')
  for (const pending of state.pendingResults) {
    clearTimeout(pending.timer)
    pending.reject(error)
  }
  state.pendingResults.clear()
  for (const waiter of state.storageWaiters) {
    clearTimeout(waiter.timer)
    waiter.reject(error)
  }
  state.storageWaiters.clear()
}

function getChannel() {
  if (typeof BroadcastChannel === 'undefined') return null
  if (!state.channel) {
    state.channel = new BroadcastChannel(AUTH_REFRESH_CHANNEL)
    state.channel.addEventListener('message', (event) => handleMessage(event.data))
  }
  return state.channel
}

function postChannel(message: RefreshMessage) {
  getChannel()?.postMessage(message)
}

function postStorageSignal(message: RefreshMessage) {
  if (typeof localStorage === 'undefined') return
  try {
    const { accessToken: _accessToken, ...safeMessage } = message
    localStorage.setItem(AUTH_REFRESH_STORAGE_EVENT_KEY, JSON.stringify({ ...safeMessage, nonce: createId() }))
    localStorage.removeItem(AUTH_REFRESH_STORAGE_EVENT_KEY)
  } catch {
    // Cross-tab coordination is best effort when storage is blocked.
  }
}

function installStorageListener() {
  if (state.storageListenerInstalled || typeof window === 'undefined') return
  window.addEventListener('storage', handleStorageEvent)
  state.storageListenerInstalled = true
}

function handleStorageEvent(event: StorageEvent) {
  if (event.key !== AUTH_REFRESH_STORAGE_EVENT_KEY || !event.newValue) return
  try {
    const message = JSON.parse(event.newValue) as unknown
    if (!isRefreshMessage(message) || message.sourceId === state.sourceId) return
    for (const waiter of state.storageWaiters) {
      if (message.type !== 'refresh-cancelled'
        && (message.type !== 'refresh-result' && message.type !== 'refresh-error'
          || (message.previousToken ?? null) !== waiter.previousToken)) continue
      clearTimeout(waiter.timer)
      state.storageWaiters.delete(waiter)
      waiter.resolve(message)
    }
  } catch {
    // Ignore malformed cross-tab signals.
  }
}

function waitForStorageCompletion(previousToken: string | null, signal: AbortSignal) {
  installStorageListener()
  return new Promise<RefreshMessage>((resolve, reject) => {
    const waiter: StorageWaiter = {
      previousToken,
      resolve,
      reject,
      timer: setTimeout(() => {
        state.storageWaiters.delete(waiter)
        reject(new Error('Timed out waiting for authentication refresh lease'))
      }, STORAGE_LEASE_MS),
    }
    state.storageWaiters.add(waiter)
    signal.addEventListener('abort', () => {
      clearTimeout(waiter.timer)
      state.storageWaiters.delete(waiter)
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
    }, { once: true })
  })
}

function readLease(): { ownerId: string, expiresAt: number } | null {
  try {
    const value = localStorage.getItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
    if (!value) return null
    const lease = JSON.parse(value) as { ownerId?: unknown, expiresAt?: unknown }
    return typeof lease.ownerId === 'string' && typeof lease.expiresAt === 'number'
      ? { ownerId: lease.ownerId, expiresAt: lease.expiresAt }
      : null
  } catch {
    return null
  }
}

async function acquireStorageLease(signal: AbortSignal) {
  if (typeof localStorage === 'undefined') return true
  const existing = readLease()
  if (existing && existing.expiresAt > Date.now() && existing.ownerId !== state.sourceId) return false
  try {
    localStorage.setItem(AUTH_REFRESH_STORAGE_LEASE_KEY, JSON.stringify({
      ownerId: state.sourceId,
      expiresAt: Date.now() + STORAGE_LEASE_MS,
    }))
    await delay(ELECTION_WINDOW_MS, signal)
    return readLease()?.ownerId === state.sourceId
  } catch {
    return true
  }
}

function releaseStorageLease(message: RefreshMessage) {
  try {
    if (readLease()?.ownerId === state.sourceId) localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
  } catch {
    // Ignore blocked storage.
  }
  postStorageSignal(message)
}

function waitForPeerResult(
  previousToken: string | null,
  signal: AbortSignal,
  timeoutMs = PEER_RESULT_TIMEOUT_MS,
  announce = false,
) {
  const latest = state.latestResult
  if (latest?.type === 'refresh-result'
    && latest.previousToken === previousToken
    && latest.accessToken
    && Date.now() - latest.at < 5000) return Promise.resolve(latest.accessToken)

  return new Promise<string>((resolve, reject) => {
    const pending: PendingResult = {
      previousToken,
      resolve,
      reject,
      timer: setTimeout(() => {
        state.pendingResults.delete(pending)
        reject(new Error('Timed out waiting for authentication refresh result'))
      }, timeoutMs),
    }
    state.pendingResults.add(pending)
    signal.addEventListener('abort', () => {
      clearTimeout(pending.timer)
      state.pendingResults.delete(pending)
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
    }, { once: true })
    if (announce) {
      postChannel({
        type: 'refresh-request',
        sourceId: state.sourceId,
        requestId: createId(),
        previousToken,
        at: Date.now(),
      })
    }
  })
}

function getLocks() {
  return typeof navigator === 'undefined'
    ? undefined
    : (navigator as Navigator & { locks?: LockManagerLike }).locks
}

export async function runWithAuthRefreshLock<T>(refresh: () => Promise<T>): Promise<T> {
  const locks = getLocks()
  return locks ? locks.request(AUTH_REFRESH_LOCK, refresh) : refresh()
}

async function coordinateWithBroadcast(
  previousToken: string | null,
  refresh: (signal: AbortSignal) => Promise<string>,
  signal: AbortSignal,
) {
  const requestId = createId()
  const ownRequest: RefreshMessage = {
    type: 'refresh-request', sourceId: state.sourceId, requestId, previousToken, at: Date.now(),
  }
  state.candidates.set(requestId, ownRequest)
  postChannel(ownRequest)
  await delay(ELECTION_WINDOW_MS, signal)

  const latest = state.latestResult
  if (latest?.type === 'refresh-result'
    && latest.previousToken === previousToken
    && latest.accessToken
    && latest.at >= ownRequest.at) return latest.accessToken

  const candidates = [...state.candidates.values()]
    .filter((candidate) => candidate.previousToken === previousToken && Date.now() - candidate.at < 1000)
    .sort((a, b) => `${a.sourceId}:${a.requestId}`.localeCompare(`${b.sourceId}:${b.requestId}`))
  const isLeader = candidates[0]?.sourceId === state.sourceId && candidates[0]?.requestId === requestId
  state.candidates.delete(requestId)
  return isLeader ? refresh(signal) : waitForPeerResult(previousToken, signal)
}

async function coordinateWithStorage(
  previousToken: string | null,
  refresh: (signal: AbortSignal) => Promise<string>,
  signal: AbortSignal,
) {
  const isOwner = await acquireStorageLease(signal)
  if (isOwner) return refresh(signal)
  const completed = await waitForStorageCompletion(previousToken, signal)
  if (completed.type === 'refresh-cancelled') {
    throw new DOMException('Authentication session changed in another tab', 'AbortError')
  }
  if (completed.type === 'refresh-error') throw new Error(completed.message || 'Authentication refresh failed')
  // Tokens remain memory-only. Compete for the next lease and rotate sequentially with the cookie.
  try {
    if (readLease()?.ownerId === completed.sourceId) localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
  } catch {
    // Storage may be blocked after the completion event.
  }
  return coordinateWithStorage(previousToken, refresh, signal)
}

export function coordinateAuthRefresh(
  refresh: ((signal: AbortSignal) => Promise<string>) | (() => Promise<string>),
  options: CoordinateAuthRefreshOptions = {},
): Promise<string> {
  const current = state.inFlight
  if (current && current.epoch === state.epoch) return current.promise

  const previousToken = options.previousToken ?? null
  const epoch = state.epoch
  const controller = new AbortController()
  options.signal?.addEventListener('abort', () => controller.abort(), { once: true })
  const execute = (signal: AbortSignal) => refresh(signal)

  const promise = (async () => {
    try {
      const locks = getLocks()
      const token = locks
        ? await locks.request(AUTH_REFRESH_LOCK, async () => {
          const latest = state.latestResult
          if (latest?.type === 'refresh-result'
            && latest.previousToken === previousToken
            && latest.accessToken
            && Date.now() - latest.at < 5000) return latest.accessToken
          if (getChannel()) {
            try {
              return await waitForPeerResult(previousToken, controller.signal, 75, true)
            } catch (error) {
              if (controller.signal.aborted) throw error
            }
          }
          return execute(controller.signal)
        })
        : getChannel()
          ? await coordinateWithBroadcast(previousToken, execute, controller.signal)
          : await coordinateWithStorage(previousToken, execute, controller.signal)

      if (epoch !== state.epoch || controller.signal.aborted) {
        throw new DOMException('Authentication refresh was cancelled', 'AbortError')
      }
      const result: RefreshMessage = {
        type: 'refresh-result', sourceId: state.sourceId, previousToken, accessToken: token, at: Date.now(),
      }
      state.latestResult = result
      postChannel(result)
      releaseStorageLease(result)
      return token
    } catch (error) {
      if (epoch !== state.epoch || controller.signal.aborted) throw error
      const message: RefreshMessage = {
        type: 'refresh-error',
        sourceId: state.sourceId,
        previousToken,
        message: error instanceof Error ? error.message : 'Authentication refresh failed',
        at: Date.now(),
      }
      postChannel(message)
      releaseStorageLease(message)
      throw error
    }
  })()
  const flight: RefreshFlight = { promise, controller, epoch, previousToken }
  state.inFlight = flight
  void promise.finally(() => {
    if (state.inFlight === flight) state.inFlight = null
  }).catch(() => undefined)
  return promise
}

export function cancelAuthRefreshCoordinator() {
  cancelLocalAuthRefresh()
  postChannel({ type: 'refresh-cancelled', sourceId: state.sourceId, at: Date.now() })
  postStorageSignal({ type: 'refresh-cancelled', sourceId: state.sourceId, at: Date.now() })
}

export function closeAuthRefreshCoordinatorForTest() {
  cancelAuthRefreshCoordinator()
  state.channel?.close()
  state.channel = null
  if (state.storageListenerInstalled && typeof window !== 'undefined') {
    window.removeEventListener('storage', handleStorageEvent)
    state.storageListenerInstalled = false
  }
  try {
    localStorage.removeItem(AUTH_REFRESH_STORAGE_EVENT_KEY)
    localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
}
