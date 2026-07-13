import { effectScope, onScopeDispose, watch, type Ref } from 'vue'

type PwaReloadBlocker = () => boolean
type PwaReloadCallback = () => void
type PwaReloadBlockerOptions = {
  retainWhileBlockedOnDispose?: boolean
}

const blockers = new Set<PwaReloadBlocker>()
let pendingReload: PwaReloadCallback | null = null

function isPwaReloadBlocked(): boolean {
  return [...blockers].some((blocker) => blocker())
}

function flushPendingPwaReload() {
  if (!pendingReload || isPwaReloadBlocked()) return

  const reload = pendingReload
  pendingReload = null
  reload()
}

export function whenPwaReloadSafe(reload: PwaReloadCallback): boolean {
  if (isPwaReloadBlocked()) {
    pendingReload ??= reload
    return false
  }

  reload()
  return true
}

export function usePwaReloadBlocker(
  blocked: Readonly<Ref<boolean>>,
  options: PwaReloadBlockerOptions = {},
) {
  const blocker = () => blocked.value
  const watcherScope = effectScope(true)
  let disposed = false

  const removeBlocker = () => {
    blockers.delete(blocker)
    watcherScope.stop()
    flushPendingPwaReload()
  }

  blockers.add(blocker)
  watcherScope.run(() => {
    watch(blocked, () => {
      flushPendingPwaReload()
      if (disposed && !blocked.value) removeBlocker()
    }, { flush: 'sync' })
  })

  onScopeDispose(() => {
    if (options.retainWhileBlockedOnDispose && blocked.value) {
      disposed = true
      return
    }
    removeBlocker()
  })
}
