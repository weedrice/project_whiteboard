const AUTH_REFRESH_LOCK = 'noviis-auth-refresh'
const AUTH_REFRESH_CHANNEL = 'noviis-auth-session'

type LockManagerLike = {
  request: <T>(name: string, callback: () => Promise<T>) => Promise<T>
}

let channel: BroadcastChannel | null = null
let refreshInFlight: Promise<string> | null = null

function getRefreshChannel() {
  if (typeof BroadcastChannel === 'undefined') return null
  channel ??= new BroadcastChannel(AUTH_REFRESH_CHANNEL)
  return channel
}

export async function runWithAuthRefreshLock<T>(refresh: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined'
    ? undefined
    : (navigator as Navigator & { locks?: LockManagerLike }).locks

  const result = locks
    ? await locks.request(AUTH_REFRESH_LOCK, refresh)
    : await refresh()

  getRefreshChannel()?.postMessage({ type: 'refresh-complete', at: Date.now() })
  return result
}

export function coordinateAuthRefresh(refresh: () => Promise<string>): Promise<string> {
  if (refreshInFlight) return refreshInFlight

  const request = runWithAuthRefreshLock(refresh)
  refreshInFlight = request
  void request.finally(() => {
    if (refreshInFlight === request) refreshInFlight = null
  }).catch(() => undefined)
  return request
}

export function closeAuthRefreshCoordinatorForTest() {
  channel?.close()
  channel = null
  refreshInFlight = null
}
