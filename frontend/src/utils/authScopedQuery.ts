import type { QueryFunctionContext, QueryKey } from '@tanstack/vue-query'
import { hashKey } from '@tanstack/query-core'
import type { AxiosRequestConfig } from 'axios'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { AUTH_SCOPED_QUERY_META, getCurrentQuerySessionSource, sessionQueryKey } from '@/queryAuthScope'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { captureAuthSessionIntent, createAuthSessionChangedError, throwIfAuthSessionIntentChanged, type AuthSessionIntent, type AuthSessionIntentSource } from '@/utils/authSessionIntent'

export type AuthScopedQueryContext<TPageParam = never> = QueryFunctionContext<QueryKey, TPageParam> & {
  requestConfig: AuthSessionScope['requestConfig']
  withRequestConfig: AuthSessionScope['withRequestConfig']
  assertCurrent: AuthSessionScope['assertCurrent']
}

// Non-component query factories also run in router guards. The generation-only
// source preserves isolated consumers which configure query scope without Pinia.
export function getAuthQuerySource(): AuthSessionIntentSource {
  return getCurrentQuerySessionSource()
}

/** Build inside computed options for reactive queries; once for fetchQuery. */
export function createAuthScopedQueryOptions<TData, TPageParam = never>(
  source: AuthSessionIntentSource,
  queryKey: QueryKey,
  request: (context: AuthScopedQueryContext<TPageParam>) => TData | Promise<TData>,
  expectedSession = captureAuthSessionIntent(source),
) {
  const intent = Object.freeze({ ...expectedSession })
  // An explicit prefixed key can describe an older queued operation. Preserve
  // its identity and reject it; never relabel it with the current generation.
  const generation = queryKey[0] === 'session' && typeof queryKey[1] === 'number'
    ? queryKey[1]
    : intent.sessionGeneration
  const key = sessionQueryKey(generation, queryKey)
  const keyHash = hashKey(key)
  return {
    queryKey: key,
    meta: AUTH_SCOPED_QUERY_META,
    queryFn: async (context: QueryFunctionContext<QueryKey, TPageParam>): Promise<TData> => {
      if (generation !== intent.sessionGeneration || hashKey(context.queryKey) !== keyHash) {
        throw createAuthSessionChangedError()
      }
      throwIfAuthSessionIntentChanged(source, intent, context.signal)
      const scope = createAuthSessionScope(source)
      try {
        const requestConfig = scope.withRequestConfig({ signal: context.signal })
        const value = await request(Object.assign({}, context, {
          signal: requestConfig.signal,
          requestConfig,
          withRequestConfig: (config?: AxiosRequestConfig): AuthSessionScope['requestConfig'] => {
            const combined = scope.withRequestConfig(config)
            return { ...combined, signal: AbortSignal.any([requestConfig.signal, combined.signal]) }
          },
          assertCurrent: scope.assertCurrent,
        }))
        scope.assertCurrent()
        if (context.signal.aborted) throw createAuthSessionChangedError()
        return value
      } catch (error) {
        scope.assertCurrent()
        if (context.signal.aborted) throw createAuthSessionChangedError()
        throw error
      } finally {
        scope.dispose()
      }
    },
  }
}

/** Keep ordinary/infinite observers on the same key and intent contract. */
export function createReactiveAuthScopedQueryOptions<TData, TPageParam = never>(
  source: AuthSessionIntentSource,
  queryKey: MaybeRefOrGetter<QueryKey>,
  request: (context: AuthScopedQueryContext<TPageParam>) => TData | Promise<TData>,
) {
  let boundIntent: AuthSessionIntent | undefined
  const options = computed(() => {
    const currentIntent = captureAuthSessionIntent(source)
    // A known principal must not change underneath an offline/retrying query.
    // Hydration from unknown to known is an explicit same-session exception;
    // login/account changes still advance the generation in the auth store.
    if (!boundIntent || boundIntent.sessionGeneration !== currentIntent.sessionGeneration || boundIntent.userId === null) {
      boundIntent = currentIntent
    }
    return createAuthScopedQueryOptions<TData, TPageParam>(source, toValue(queryKey), request, boundIntent)
  })
  return {
    queryKey: computed(() => options.value.queryKey),
    meta: AUTH_SCOPED_QUERY_META,
    queryFn: (context: QueryFunctionContext<QueryKey, TPageParam>) => options.value.queryFn(context),
  }
}
