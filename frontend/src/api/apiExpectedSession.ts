import type { AxiosRequestConfig } from 'axios'
import type { AuthStoreLike } from '@/api/authRefreshSession'
import { resolveAuthStore } from '@/api/apiStoreResolvers'
import {
  createAuthSessionChangedError,
  throwIfAuthSessionIntentChanged,
} from '@/utils/authSessionIntent'

/** Validate the invocation's original identity without replacing it on retries. */
export function assertExpectedSession(
  config: AxiosRequestConfig | undefined,
  authStore: AuthStoreLike | null,
): void {
  if (!config?.expectedSession) return
  if (!authStore) throw createAuthSessionChangedError()
  throwIfAuthSessionIntentChanged(authStore, config.expectedSession, config.signal)
}

/** Keep validation and the guarded operation in the same synchronous continuation. */
export function withExpectedSession<T>(
  config: AxiosRequestConfig | undefined,
  operation: () => T,
): T | Promise<T> {
  if (!config?.expectedSession) return operation()
  return resolveAuthStore().then((authStore) => {
    assertExpectedSession(config, authStore)
    return operation()
  })
}
