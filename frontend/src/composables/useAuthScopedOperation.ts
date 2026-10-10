import { computed, getCurrentScope, onScopeDispose, shallowRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import type { AuthSessionIntentSource } from '@/utils/authSessionIntent'

/** Local UI ownership around a complete action, including confirmation and queued work. */
export function useAuthScopedOperation<Key = string>(
  source: AuthSessionIntentSource = useAuthStore(),
) {
  const pending = shallowRef(new Map<Key, { id: symbol, scope: AuthSessionScope }>())
  let disposed = false
  function reset() {
    pending.value.forEach(({ scope }) => {
      scope.cancel()
      scope.dispose()
    })
    pending.value = new Map()
  }
  useAuthSessionReset(reset, source)
  if (getCurrentScope()) onScopeDispose(() => {
    disposed = true
    reset()
  })

  async function run<T>(key: Key, action: (scope: AuthSessionScope) => T | Promise<T>): Promise<T | undefined> {
    if (disposed || pending.value.has(key)) return undefined
    const id = Symbol()
    const scope = createAuthSessionScope(source)
    pending.value = new Map(pending.value).set(key, { id, scope })
    try {
      const result = await action(scope)
      scope.assertCurrent()
      return result
    } catch (error) {
      if (!scope.isCurrent()) return undefined
      throw error
    } finally {
      if (pending.value.get(key)?.id === id) {
        const next = new Map(pending.value)
        next.delete(key)
        pending.value = next
      }
      scope.dispose()
    }
  }

  return {
    run,
    isPending: (key: Key) => pending.value.has(key),
    isAnyPending: computed(() => pending.value.size > 0),
  }
}
