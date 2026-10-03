import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import {
  useMutation,
  type MutateOptions,
  type MutationFunctionContext,
  type MutationObserverOptions,
  type QueryClient,
  type UseMutationReturnType,
} from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import type { AuthSessionIntent } from '@/utils/authSessionIntent'

export interface AuthScopedMutationFunctionContext extends MutationFunctionContext {
  requestConfig: AxiosRequestConfig & { expectedSession: AuthSessionIntent, signal: AbortSignal }
  withRequestConfig: (config?: AxiosRequestConfig) => AxiosRequestConfig
  assertCurrent: () => void
}

type Options<TData, TError, TVariables, TContext> = Omit<
  MutationObserverOptions<TData, TError, TVariables, TContext>, 'mutationFn' | 'onMutate'
> & {
  mutationFn: (variables: TVariables, context: AuthScopedMutationFunctionContext) => Promise<TData>
  onMutate?: (variables: TVariables, context: AuthScopedMutationFunctionContext) => TContext | Promise<TContext>
  /** Resource cleanup only. Unlike UI callbacks, this also runs after session changes. */
  onCleanup?: (data: TData | undefined, error: TError | null, variables: TVariables, result: TContext | undefined, context: AuthScopedMutationFunctionContext) => unknown
}

interface Invocation<TVariables> {
  variables: TVariables
  scope: AuthSessionScope
  cleaned?: boolean
}

/** Bind every invocation, including paused/retried mutations, to its initiating session. */
export function useAuthScopedMutation<TData = unknown, TError = Error, TVariables = void, TContext = unknown>(
  options: MaybeRefOrGetter<Options<TData, TError, TVariables, TContext>>,
  queryClient?: QueryClient,
): UseMutationReturnType<TData, TError, TVariables, TContext> {
  const authStore = useAuthStore()

  const isCurrent = (invocation: Invocation<TVariables>) => invocation.scope.isCurrent()
  const assertCurrent = (invocation: Invocation<TVariables>) => invocation.scope.assertCurrent()
  const contextFor = (invocation: Invocation<TVariables>, context: MutationFunctionContext): AuthScopedMutationFunctionContext => ({
    ...context,
    requestConfig: invocation.scope.requestConfig,
    withRequestConfig: invocation.scope.withRequestConfig,
    assertCurrent: invocation.scope.assertCurrent,
  })

  const mutation = useMutation<TData, TError, Invocation<TVariables>, TContext>(computed(() => {
    const { onCleanup, ...original } = toValue(options)
    return {
      ...original,
      mutationFn: async (invocation: Invocation<TVariables>, context: MutationFunctionContext) => {
        assertCurrent(invocation)
        try {
          const data = await original.mutationFn(invocation.variables, contextFor(invocation, context))
          assertCurrent(invocation)
          return data
        } catch (error) {
          assertCurrent(invocation)
          throw error
        }
      },
      onMutate: async (invocation: Invocation<TVariables>, context: MutationFunctionContext) => {
        assertCurrent(invocation)
        try {
          const result = await original.onMutate?.(invocation.variables, contextFor(invocation, context))
          assertCurrent(invocation)
          return result as TContext
        } catch (error) {
          assertCurrent(invocation)
          throw error
        }
      },
      onSuccess: async (data: TData, invocation: Invocation<TVariables>, result: TContext, context: MutationFunctionContext) => {
        assertCurrent(invocation)
        try {
          await original.onSuccess?.(data, invocation.variables, result, context)
          assertCurrent(invocation)
        } catch (error) {
          assertCurrent(invocation)
          throw error
        }
      },
      onError: (error: TError, invocation: Invocation<TVariables>, result: TContext | undefined, context: MutationFunctionContext) => {
        if (isCurrent(invocation)) return original.onError?.(error, invocation.variables, result, context)
      },
      onSettled: async (data: TData | undefined, error: TError | null, invocation: Invocation<TVariables>, result: TContext | undefined, context: MutationFunctionContext) => {
        try {
          if (isCurrent(invocation)) {
            await original.onSettled?.(data, error, invocation.variables, result, context)
            assertCurrent(invocation)
          }
        } finally {
          if (!invocation.cleaned) {
            invocation.cleaned = true
            await onCleanup?.(data, error, invocation.variables, result, contextFor(invocation, context))
          }
        }
      },
    }
  }), queryClient)

  const mutateAsync = async (variables: TVariables, callbacks?: MutateOptions<TData, TError, TVariables, TContext>) => {
    // Capture synchronously, before Vue Query awaits its global or local onMutate.
    const invocation = { variables, scope: createAuthSessionScope(authStore) }
    // Keep the listener until settlement even if the observer unmounts: a valid
    // same-session mutation must still finish and invalidate its shared caches.
    try {
      const data = await mutation.mutateAsync(invocation, {
        onSuccess: (data, _, result, context) => {
          if (isCurrent(invocation)) callbacks?.onSuccess?.(data, variables, result, context)
        },
        onError: (error, _, result, context) => {
          if (isCurrent(invocation)) callbacks?.onError?.(error, variables, result, context)
        },
        onSettled: (data, error, _, result, context) => {
          if (isCurrent(invocation)) callbacks?.onSettled?.(data, error, variables, result, context)
        },
      })
      assertCurrent(invocation)
      return data
    } catch (error) {
      assertCurrent(invocation)
      throw error
    } finally {
      invocation.scope.dispose()
    }
  }

  // Only the internal variable envelope differs from Vue Query's public result.
  return {
    ...mutation,
    variables: computed(() => mutation.variables.value?.variables),
    mutate: (variables: TVariables, callbacks?: MutateOptions<TData, TError, TVariables, TContext>) => {
      void mutateAsync(variables, callbacks).catch(() => undefined)
    },
    mutateAsync,
  } as UseMutationReturnType<TData, TError, TVariables, TContext>
}
