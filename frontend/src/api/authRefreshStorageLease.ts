export const AUTH_REFRESH_STORAGE_LEASE_KEY = 'noviisAuthRefreshLease'
export const STORAGE_LEASE_MS = 30_000
export const STORAGE_LEASE_HEARTBEAT_MS = 5_000
export const STORAGE_TAKEOVER_JITTER_MS = 120
export const STORAGE_ELECTION_WINDOW_MS = 40

export type StorageLease = {
  ownerId: string
  sessionId: string
  fence: number
  expiresAt: number
}

export function readStorageLease(): StorageLease | null {
  try {
    const value = localStorage.getItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
    if (!value) return null
    const lease = JSON.parse(value) as {
      ownerId?: unknown
      sessionId?: unknown
      fence?: unknown
      expiresAt?: unknown
    }
    return typeof lease.ownerId === 'string' && typeof lease.sessionId === 'string'
      && typeof lease.expiresAt === 'number'
      && (lease.fence === undefined || typeof lease.fence === 'number')
      ? {
          ownerId: lease.ownerId,
          sessionId: lease.sessionId,
          fence: lease.fence ?? 0,
          expiresAt: lease.expiresAt,
        }
      : null
  } catch {
    return null
  }
}

export function sameStorageLease(
  left: StorageLease | null,
  right: StorageLease | null,
): boolean {
  return Boolean(left && right
    && left.ownerId === right.ownerId
    && left.sessionId === right.sessionId
    && left.fence === right.fence)
}

function delay(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => {
      clearTimeout(timer)
      reject(new DOMException('Authentication refresh was cancelled', 'AbortError'))
    }, { once: true })
  })
}

export async function acquireStorageLease(
  ownerId: string,
  sessionId: string,
  signal: AbortSignal,
): Promise<StorageLease | null> {
  if (typeof localStorage === 'undefined') {
    return { ownerId, sessionId, fence: 0, expiresAt: Number.POSITIVE_INFINITY }
  }
  const existing = readStorageLease()
  if (existing && existing.sessionId === sessionId
    && existing.expiresAt > Date.now() && existing.ownerId !== ownerId) return null
  try {
    const candidate: StorageLease = {
      ownerId,
      sessionId,
      fence: Math.max(existing?.fence ?? 0, Date.now()) + 1,
      expiresAt: Date.now() + STORAGE_LEASE_MS,
    }
    localStorage.setItem(AUTH_REFRESH_STORAGE_LEASE_KEY, JSON.stringify(candidate))
    await delay(
      STORAGE_ELECTION_WINDOW_MS + Math.floor(Math.random() * STORAGE_TAKEOVER_JITTER_MS),
      signal,
    )
    return sameStorageLease(readStorageLease(), candidate) ? candidate : null
  } catch {
    return { ownerId, sessionId, fence: 0, expiresAt: Number.POSITIVE_INFINITY }
  }
}

export function renewStorageLease(lease: StorageLease): boolean {
  try {
    const current = readStorageLease()
    if (!sameStorageLease(current, lease)) return false
    const expiresAt = Date.now() + STORAGE_LEASE_MS
    localStorage.setItem(AUTH_REFRESH_STORAGE_LEASE_KEY, JSON.stringify({ ...lease, expiresAt }))
    lease.expiresAt = expiresAt
    return true
  } catch {
    return false
  }
}

export function releaseStorageLease(lease: StorageLease | null): boolean {
  let didOwnLease = lease?.fence === 0
  try {
    if (sameStorageLease(readStorageLease(), lease)) {
      didOwnLease = true
      localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
    }
  } catch {
    didOwnLease = true
  }
  return Boolean(didOwnLease)
}

export function releaseStorageLeaseOwnedBy(ownerId: string) {
  try {
    if (readStorageLease()?.ownerId === ownerId) {
      localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
    }
  } catch {
    // Storage may be blocked after the completion event.
  }
}

export function clearStorageLeaseForTest() {
  try {
    localStorage.removeItem(AUTH_REFRESH_STORAGE_LEASE_KEY)
  } catch {
    // Ignore unavailable storage in non-browser tests.
  }
}
