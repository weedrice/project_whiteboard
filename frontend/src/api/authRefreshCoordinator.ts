import {
  AUTH_REFRESH_CHANNEL,
  AUTH_REFRESH_STORAGE_EVENT_KEY,
  createAuthRefreshId,
  getOrCreateSharedAuthSessionId,
  isRefreshMessage,
  rotateSharedAuthSessionId as rotateProtocolAuthSessionId,
  type RefreshMessage,
} from '@/api/authRefreshProtocol'
import {
  STORAGE_ELECTION_WINDOW_MS,
  STORAGE_LEASE_HEARTBEAT_MS,
  STORAGE_LEASE_MS,
  STORAGE_TAKEOVER_JITTER_MS,
  acquireStorageLease,
  clearStorageLeaseForTest,
  readStorageLease as readLease,
  releaseStorageLease as releaseOwnedStorageLease,
  releaseStorageLeaseOwnedBy,
  renewStorageLease,
  sameStorageLease,
  type StorageLease,
} from '@/api/authRefreshStorageLease'

const AUTH_REFRESH_LOCK = 'noviis-auth-refresh'
const ELECTION_WINDOW_MS = STORAGE_ELECTION_WINDOW_MS
const PEER_RESULT_TIMEOUT_MS = 15_000

type LockManagerLike = {
  request: <T>(name: string, callback: () => Promise<T>) => Promise<T>
}

type RefreshFlight = {
  promise: Promise<string>
  controller: AbortController
  epoch: number
  previousToken: string | null
  sessionId: string
}

type PendingResult = {
  sessionId: string
  previousToken: string | null
  resolve: (token: string) => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}

type StorageWaiter = {
  sessionId: string
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
  activeStorageLease: StorageLease | null
}

export interface CoordinateAuthRefreshOptions {
  previousToken?: string | null
  sessionId?: string
  signal?: AbortSignal
}

const GLOBAL_STATE_KEY = '__noviisAuthRefreshCoordinator__'
const globalState = globalThis as typeof globalThis & { [GLOBAL_STATE_KEY]?: CoordinatorState }
const state = globalState[GLOBAL_STATE_KEY] ??= {
  sourceId: createAuthRefreshId(),
  epoch: 0,
  channel: null,
  inFlight: null,
  latestResult: null,
  candidates: new Map(),
  pendingResults: new Set(),
  storageWaiters: new Set(),
  storageListenerInstalled: false,
  activeStorageLease: null,
}

/** Rotates the non-secret, cross-tab coordination identity at an auth boundary. */
export function rotateSharedAuthSessionId(): string {
  return rotateProtocolAuthSessionId()
}

function createRefreshAbortError() {
  return new DOMException('Authentication refresh was cancelled', 'AbortError')
}

class PeerRefreshCompletedError extends Error {
  constructor() {
    super('A peer completed a different access-token refresh generation')
    this.name = 'PeerRefreshCompletedError'
  }
}

class StorageLeaseLostError extends Error {
  constructor() {
    super('Authentication refresh storage lease was lost')
    this.name = 'StorageLeaseLostError'
  }
}

function waitForRefreshFlight(promise: Promise<string>, signal?: AbortSignal) {
  if (!signal) return promise
  if (signal.aborted) return Promise.reject(createRefreshAbortError())

  return new Promise<string>((resolve, reject) => {
    const handleAbort = () => reject(createRefreshAbortError())
    signal.addEventListener('abort', handleAbort, { once: true })
    promise.then(
      (token) => {
        signal.removeEventListener('abort', handleAbort)
        resolve(token)
      },
      (error: unknown) => {
        signal.removeEventListener('abort', handleAbort)
        reject(error)
      },
    )
  })
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

function notifyPendingResults(message: RefreshMessage) {
  if (message.type !== 'refresh-result' && message.type !== 'refresh-error') return
  for (const pending of state.pendingResults) {
    if (pending.sessionId !== message.sessionId) continue
    clearTimeout(pending.timer)
    state.pendingResults.delete(pending)
    if (pending.previousToken !== (message.previousToken ?? null)) {
      pending.reject(new PeerRefreshCompletedError())
      continue
    }
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
      && latest.sessionId === value.sessionId
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
  if (value.sessionId) {
    for (const [requestId, candidate] of state.candidates) {
      if (candidate.sessionId === value.sessionId) state.candidates.delete(requestId)
    }
  }
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
    localStorage.setItem(AUTH_REFRESH_STORAGE_EVENT_KEY, JSON.stringify({
      ...safeMessage,
      nonce: createAuthRefreshId(),
    }))
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
          || message.sessionId !== waiter.sessionId)) continue
      clearTimeout(waiter.timer)
      state.storageWaiters.delete(waiter)
      waiter.resolve(message)
    }
  } catch {
    // Ignore malformed cross-tab signals.
  }
}

function waitForStorageCompletion(sessionId: string, signal: AbortSignal, timeoutMs = STORAGE_LEASE_MS) {
  installStorageListener()
  return new Promise<RefreshMessage>((resolve, reject) => {
    const waiter: StorageWaiter = {
      sessionId,
      resolve,
      reject,
      timer: setTimeout(() => {
        state.storageWaiters.delete(waiter)
        reject(new Error('Timed out waiting for authentication refresh lease'))
      }, timeoutMs),
    }
    state.storageWaiters.add(waiter)
    signal.addEventListener('abort', () => {
      clearTimeout(waiter.timer)
      state.storageWaiters.delete(waiter)
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
    }, { once: true })
  })
}

function releaseStorageLease(message: RefreshMessage) {
  const activeLease = state.activeStorageLease
  const didOwnLease = releaseOwnedStorageLease(activeLease)
  state.activeStorageLease = null
  if (didOwnLease || !activeLease) postStorageSignal(message)
}

function waitForPeerResult(
  sessionId: string,
  previousToken: string | null,
  signal: AbortSignal,
  timeoutMs = PEER_RESULT_TIMEOUT_MS,
  announce = false,
) {
  const latest = state.latestResult
  if (latest?.type === 'refresh-result'
    && latest.sessionId === sessionId
    && latest.previousToken === previousToken
    && latest.accessToken
    && Date.now() - latest.at < 5000) return Promise.resolve(latest.accessToken)

  return new Promise<string>((resolve, reject) => {
    const pending: PendingResult = {
      sessionId,
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
        requestId: createAuthRefreshId(),
        sessionId,
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
  sessionId: string,
  previousToken: string | null,
  refresh: (signal: AbortSignal) => Promise<string>,
  signal: AbortSignal,
): Promise<string> {
  const requestId = createAuthRefreshId()
  const ownRequest: RefreshMessage = {
    type: 'refresh-request', sourceId: state.sourceId, requestId, sessionId, previousToken, at: Date.now(),
  }
  state.candidates.set(requestId, ownRequest)
  postChannel(ownRequest)
  await delay(ELECTION_WINDOW_MS, signal)

  const latest = state.latestResult
  if (latest?.type === 'refresh-result'
    && latest.sessionId === sessionId
    && latest.accessToken
    && latest.at >= ownRequest.at) {
    return latest.previousToken === previousToken
      ? latest.accessToken
      : coordinateWithBroadcast(sessionId, previousToken, refresh, signal)
  }

  const candidates = [...state.candidates.values()]
    .filter((candidate) => candidate.sessionId === sessionId && Date.now() - candidate.at < 1000)
    .sort((a, b) => `${a.sourceId}:${a.requestId}`.localeCompare(`${b.sourceId}:${b.requestId}`))
  const isLeader = candidates[0]?.sourceId === state.sourceId && candidates[0]?.requestId === requestId
  state.candidates.delete(requestId)
  if (isLeader) return refresh(signal)
  try {
    return await waitForPeerResult(sessionId, previousToken, signal)
  } catch (error) {
    if (error instanceof PeerRefreshCompletedError) {
      return coordinateWithBroadcast(sessionId, previousToken, refresh, signal)
    }
    throw error
  }
}

async function coordinateWithStorage(
  sessionId: string,
  previousToken: string | null,
  refresh: (signal: AbortSignal) => Promise<string>,
  signal: AbortSignal,
) {
  const lease = await acquireStorageLease(state.sourceId, sessionId, signal)
  if (lease) {
    state.activeStorageLease = lease
    const leaseController = new AbortController()
    const abortForSessionBoundary = () => leaseController.abort(createRefreshAbortError())
    signal.addEventListener('abort', abortForSessionBoundary, { once: true })
    const heartbeat = lease.fence === 0 ? null : setInterval(() => {
      if (!renewStorageLease(lease)) {
        leaseController.abort(new StorageLeaseLostError())
      }
    }, STORAGE_LEASE_HEARTBEAT_MS)
    try {
      const token = await refresh(leaseController.signal)
      if (leaseController.signal.aborted || (lease.fence !== 0 && !sameStorageLease(readLease(), lease))) {
        throw new StorageLeaseLostError()
      }
      return token
    } finally {
      if (heartbeat) clearInterval(heartbeat)
      signal.removeEventListener('abort', abortForSessionBoundary)
    }
  }
  const currentLease = readLease()
  const waitMs = currentLease && currentLease.sessionId === sessionId
    ? Math.max(ELECTION_WINDOW_MS, currentLease.expiresAt - Date.now() + STORAGE_TAKEOVER_JITTER_MS)
    : ELECTION_WINDOW_MS + STORAGE_TAKEOVER_JITTER_MS
  let completed: RefreshMessage
  try {
    completed = await waitForStorageCompletion(sessionId, signal, waitMs)
  } catch (error) {
    if (signal.aborted) throw error
    return coordinateWithStorage(sessionId, previousToken, refresh, signal)
  }
  if (completed.type === 'refresh-cancelled') {
    throw new DOMException('Authentication session changed in another tab', 'AbortError')
  }
  if (completed.type === 'refresh-error') throw new Error(completed.message || 'Authentication refresh failed')
  // Tokens remain memory-only. Compete for the next lease and rotate sequentially with the cookie.
  releaseStorageLeaseOwnedBy(completed.sourceId)
  return coordinateWithStorage(sessionId, previousToken, refresh, signal)
}

export function coordinateAuthRefresh(
  refresh: ((signal: AbortSignal) => Promise<string>) | (() => Promise<string>),
  options: CoordinateAuthRefreshOptions = {},
): Promise<string> {
  if (options.signal?.aborted) return Promise.reject(createRefreshAbortError())
  const current = state.inFlight
  if (current && current.epoch === state.epoch) {
    return waitForRefreshFlight(current.promise, options.signal)
  }

  const previousToken = options.previousToken ?? null
  const sessionId = options.sessionId ?? getOrCreateSharedAuthSessionId()
  const epoch = state.epoch
  const controller = new AbortController()
  const execute = (signal: AbortSignal) => refresh(signal)

  const promise = (async () => {
    try {
      const locks = getLocks()
      const token = locks
        ? await locks.request(AUTH_REFRESH_LOCK, async () => {
          const latest = state.latestResult
          if (latest?.type === 'refresh-result'
            && latest.sessionId === sessionId
            && latest.previousToken === previousToken
            && latest.accessToken
            && Date.now() - latest.at < 5000) return latest.accessToken
          if (getChannel()) {
            try {
              return await waitForPeerResult(sessionId, previousToken, controller.signal, 75, true)
            } catch (error) {
              if (controller.signal.aborted) throw error
              if (error instanceof PeerRefreshCompletedError) return execute(controller.signal)
            }
          }
          return execute(controller.signal)
        })
        : getChannel()
          ? await coordinateWithBroadcast(sessionId, previousToken, execute, controller.signal)
          : await coordinateWithStorage(sessionId, previousToken, execute, controller.signal)

      if (epoch !== state.epoch || controller.signal.aborted) {
        throw new DOMException('Authentication refresh was cancelled', 'AbortError')
      }
      if (state.activeStorageLease?.fence
        && !sameStorageLease(readLease(), state.activeStorageLease)) {
        state.activeStorageLease = null
        throw new StorageLeaseLostError()
      }
      const result: RefreshMessage = {
        type: 'refresh-result', sourceId: state.sourceId, sessionId, previousToken, accessToken: token, at: Date.now(),
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
        sessionId,
        previousToken,
        message: error instanceof Error ? error.message : 'Authentication refresh failed',
        at: Date.now(),
      }
      if (!(error instanceof StorageLeaseLostError)) {
        postChannel(message)
        releaseStorageLease(message)
      } else {
        state.activeStorageLease = null
      }
      throw error
    }
  })()
  const flight: RefreshFlight = { promise, controller, epoch, previousToken, sessionId }
  state.inFlight = flight
  void promise.finally(() => {
    if (state.inFlight === flight) state.inFlight = null
  }).catch(() => undefined)
  return waitForRefreshFlight(promise, options.signal)
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
    clearStorageLeaseForTest()
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
  state.activeStorageLease = null
}
