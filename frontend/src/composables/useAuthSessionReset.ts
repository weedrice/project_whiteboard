import { getCurrentScope, onScopeDispose, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import {
  captureAuthSessionIntent,
  isAuthSessionIntentCurrent,
  type AuthSessionIntentSource,
} from '@/utils/authSessionIntent'

/** Reset local copies as well as requests; ordinary token rotation is not a reset. */
export function useAuthSessionReset(
  reset: () => void,
  source: AuthSessionIntentSource = useAuthStore(),
) {
  let previous = captureAuthSessionIntent(source)
  let stopped = false
  const resetIfChanged = () => {
    if (stopped || isAuthSessionIntentCurrent(source, previous)) return
    previous = captureAuthSessionIntent(source)
    reset()
  }
  const stopWatch = watch(
    () => [source.sessionGeneration, source.user?.userId] as const,
    resetIfChanged,
    { flush: 'sync' },
  )
  const unsubscribe = subscribeAuthSessionBoundary((generation) => {
    if (stopped) return
    // Also support an explicit boundary delivered before a non-reactive source updates.
    if (generation !== source.sessionGeneration) {
      previous = captureAuthSessionIntent(source)
      reset()
    } else {
      resetIfChanged()
    }
  })
  const stop = () => {
    if (stopped) return
    stopped = true
    stopWatch()
    unsubscribe()
  }
  if (getCurrentScope()) onScopeDispose(stop)
  return stop
}
