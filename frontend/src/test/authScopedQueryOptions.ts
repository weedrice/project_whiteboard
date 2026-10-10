import { expect } from 'vitest'
import { toValue, type MaybeRefOrGetter } from 'vue'
import type { QueryKey } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'

/** Give lightweight query mocks the context that real Vue Query supplies. */
export function queryTestContext<T extends Record<string, unknown> = Record<string, never>>(
  options: object,
  context: T = {} as T,
) {
  return {
    ...context,
    queryKey: toValue((options as { queryKey?: unknown }).queryKey as MaybeRefOrGetter<QueryKey>),
    signal: context.signal instanceof AbortSignal ? context.signal : new AbortController().signal,
  }
}

export function authQueryConfig(extra: AxiosRequestConfig = {}) {
  return expect.objectContaining({
    ...extra,
    signal: expect.any(AbortSignal),
    expectedSession: expect.objectContaining({ sessionGeneration: expect.any(Number) }),
  })
}
