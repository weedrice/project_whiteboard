import type { AxiosRequestConfig, GenericAbortSignal } from 'axios'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import {
  captureAuthSessionIntent,
  isAuthSessionIntentCurrent,
  throwIfAuthSessionIntentChanged,
  type AuthSessionIntent,
  type AuthSessionIntentSource,
} from '@/utils/authSessionIntent'

type ScopedRequestConfig = AxiosRequestConfig & {
  expectedSession: AuthSessionIntent
  signal: AbortSignal
}

export interface AuthSessionScope {
  readonly intent: AuthSessionIntent
  readonly requestConfig: ScopedRequestConfig
  withRequestConfig: (config?: AxiosRequestConfig) => ScopedRequestConfig
  isCurrent: () => boolean
  assertCurrent: () => void
  /** Only synchronous side effects are guarded; check again after every await. */
  runIfCurrent: <T>(operation: () => T) => T | undefined
  /** Release listeners on settlement, without cancelling a completed request. */
  dispose: () => void
}

/** Capture at the start of a user action, before confirmation, queuing or HTTP. */
export function createAuthSessionScope(source: AuthSessionIntentSource): AuthSessionScope {
  const intent = Object.freeze(captureAuthSessionIntent(source))
  const controller = new AbortController()
  const unsubscribe = subscribeAuthSessionBoundary(() => controller.abort())
  const signalCleanups = new Set<() => void>()
  let disposed = false

  const isCurrent = () => !controller.signal.aborted && isAuthSessionIntentCurrent(source, intent)
  const assertCurrent = () => throwIfAuthSessionIntentChanged(source, intent, controller.signal)

  function combineSignal(signal?: GenericAbortSignal): AbortSignal {
    if (!signal || signal === controller.signal) return controller.signal
    if (signal instanceof AbortSignal) return AbortSignal.any([controller.signal, signal])

    // Axios also accepts signals from other realms or adapters. Bridge their
    // events rather than losing session cancellation when merging the config.
    const combined = new AbortController()
    if (signal.aborted || controller.signal.aborted) {
      combined.abort()
      return combined.signal
    }
    const abort = () => combined.abort()
    const cleanup = () => {
      signal.removeEventListener?.('abort', abort)
      controller.signal.removeEventListener('abort', abort)
      signalCleanups.delete(cleanup)
    }
    signalCleanups.add(cleanup)
    combined.signal.addEventListener('abort', cleanup, { once: true })
    signal.addEventListener?.('abort', abort, { once: true })
    controller.signal.addEventListener('abort', abort, { once: true })
    return combined.signal
  }

  return {
    intent,
    requestConfig: { expectedSession: intent, signal: controller.signal },
    withRequestConfig: (config) => ({
      ...config,
      expectedSession: intent,
      signal: combineSignal(config?.signal),
    }),
    isCurrent,
    assertCurrent,
    runIfCurrent: (operation) => isCurrent() ? operation() : undefined,
    dispose: () => {
      if (disposed) return
      disposed = true
      unsubscribe()
      signalCleanups.forEach(cleanup => cleanup())
    },
  }
}

/**
 * Run a manual UI action in its initiating session. Session changes quietly
 * discard results/errors; ordinary failures remain available to error handlers.
 * The action must use assertCurrent/runIfCurrent after its own awaits, and pass
 * requestConfig to each API call. Authentication lifecycle actions are separate.
 */
export async function runAuthScopedAction<T>(
  source: AuthSessionIntentSource,
  action: (scope: AuthSessionScope) => T | Promise<T>,
): Promise<T | undefined> {
  const scope = createAuthSessionScope(source)
  try {
    const result = await action(scope)
    scope.assertCurrent()
    return result
  } catch (error) {
    if (!scope.isCurrent()) return undefined
    throw error
  } finally {
    scope.dispose()
  }
}
