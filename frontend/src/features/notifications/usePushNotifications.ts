import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userSettingsQueryKey } from '@/composables/useUser'
import {
  deleteBrowserPushSubscription,
  getBrowserPushSubscription,
  getNotificationPermission,
  isPushSupported,
  requestPushPermission,
  saveBrowserPushSubscription,
  subscribeBrowserPush,
} from '@/features/notifications/pushSubscriptions'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey, isSessionGenerationCurrent, subscribeAuthSessionBoundary } from '@/queryAuthScope'
import {
  captureAuthSessionIntent,
  isAuthSessionIntentCurrent,
  throwIfAuthSessionIntentChanged,
} from '@/utils/authSessionIntent'
import { isCancellationError } from '@/utils/cancellationError'

export function usePushNotifications() {
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const permission = ref<NotificationPermission | 'unsupported'>(getNotificationPermission())
  const operationControllers = new Set<AbortController>()
  const stopSessionBoundaryListener = subscribeAuthSessionBoundary(() => {
    operationControllers.forEach((controller) => controller.abort())
    operationControllers.clear()
  })
  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopSessionBoundaryListener()
      operationControllers.forEach((controller) => controller.abort())
      operationControllers.clear()
    })
  }

  const startSessionOperation = () => {
    const controller = new AbortController()
    operationControllers.add(controller)
    return controller
  }
  const publicKeyQuery = useQuery({
    queryKey: ['push', 'public-key'],
    queryFn: async ({ signal }) => unwrapAxiosApiData(await userApi.getPushPublicKey({ signal })),
    staleTime: QUERY_STALE_TIME.LONG,
    enabled: isPushSupported(),
  })

  const enabled = computed(() => Boolean(publicKeyQuery.data.value?.enabled && publicKeyQuery.data.value.publicKey))
  const supported = computed(() => isPushSupported())

  const enableMutation = useMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: async () => {
      const intent = captureAuthSessionIntent(authStore)
      const controller = startSessionOperation()
      let subscription: PushSubscription | null = null
      try {
        if (!enabled.value || !publicKeyQuery.data.value?.publicKey) {
          throw new Error('Web push is not configured.')
        }
        const nextPermission = await requestPushPermission()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        permission.value = nextPermission
        if (nextPermission !== 'granted') {
          throw new Error('Push permission was not granted.')
        }
        subscription = await subscribeBrowserPush(publicKeyQuery.data.value.publicKey)
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await saveBrowserPushSubscription(subscription, { signal: controller.signal })
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
      } catch (error) {
        if (subscription && (
          isCancellationError(error)
          || !isAuthSessionIntentCurrent(authStore, intent)
        )) {
          await subscription.unsubscribe().catch(() => false)
        }
        throw error
      } finally {
        operationControllers.delete(controller)
      }
    },
    onSuccess: (_data, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, userSettingsQueryKey),
      })
    },
  })

  const disableMutation = useMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: async () => {
      const intent = captureAuthSessionIntent(authStore)
      const controller = startSessionOperation()
      try {
        const subscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (!subscription) {
          return
        }
        await deleteBrowserPushSubscription(subscription, { signal: controller.signal })
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await subscription.unsubscribe()
      } finally {
        operationControllers.delete(controller)
      }
    },
    onSuccess: (_data, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, userSettingsQueryKey),
      })
    },
  })

  return {
    enabled,
    supported,
    permission,
    publicKey: publicKeyQuery.data,
    isLoading: publicKeyQuery.isLoading,
    isError: publicKeyQuery.isError,
    error: publicKeyQuery.error,
    refetch: publicKeyQuery.refetch,
    isEnabling: enableMutation.isPending,
    isDisabling: disableMutation.isPending,
    enablePush: enableMutation.mutateAsync,
    disablePush: disableMutation.mutateAsync,
  }
}
