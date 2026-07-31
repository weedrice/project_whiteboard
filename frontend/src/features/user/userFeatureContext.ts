import { useQueryClient } from '@tanstack/vue-query'
import { currentSessionQueryKey, isSessionGenerationCurrent } from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'

export function createUserFeatureContext() {
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)
  const captureMutationSession = () => ({ sessionGeneration: authStore.sessionGeneration })
  const isCurrentMutation = (context?: { sessionGeneration: number }) => (
    context !== undefined && isSessionGenerationCurrent(authStore, context.sessionGeneration)
  )

  return {
    queryClient,
    authStore,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  }
}

export type UserFeatureContext = ReturnType<typeof createUserFeatureContext>

export async function resolveResponseData<T>(request: Promise<{ data: T }>): Promise<T> {
  const { data } = await request
  return data
}
