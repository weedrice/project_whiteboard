import { expect } from 'vitest'
import { captureAuthSessionIntent, throwIfAuthSessionIntentChanged, type AuthSessionIntentSource } from '@/utils/authSessionIntent'

export const authMutationConfig = (config: Record<string, unknown> = {}) => expect.objectContaining({
  ...config,
  expectedSession: expect.objectContaining({ sessionGeneration: expect.any(Number) }),
  signal: expect.any(AbortSignal),
})

export async function failAuthScopedMutation(options: any, error: Error, variables: Record<string, unknown>) {
  await options?.onError?.(error, variables)
  await options?.onSettled?.(undefined, error, variables, undefined)
}

/**
 * Feature unit tests stub Vue Query to inspect cache effects. Adapt that stub at
 * the public composable boundary; session scheduling has separate real-runtime tests.
 */
export function authScopedMutationOptions(options: Record<string, any>, resolveStore: () => AuthSessionIntentSource) {
  const context = () => {
    const store = resolveStore()
    const expectedSession = captureAuthSessionIntent(store)
    const signal = new AbortController().signal
    return {
      requestConfig: { expectedSession, signal },
      withRequestConfig: (config?: Record<string, unknown>) => ({ ...config, expectedSession, signal: config?.signal ?? signal }),
      assertCurrent: () => throwIfAuthSessionIntentChanged(store, expectedSession, signal),
    }
  }
  return {
    ...options,
    mutationFn: (variables: unknown) => options.mutationFn(variables, context()),
    onMutate: (variables: unknown) => options.onMutate?.(variables, context()),
    onSettled: async (...args: unknown[]) => {
      try {
        return await options.onSettled?.(...args)
      } finally {
        await options.onCleanup?.(...args.slice(0, 4), context())
      }
    },
  }
}
