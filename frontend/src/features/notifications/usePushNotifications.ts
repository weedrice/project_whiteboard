import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userSettingsQueryKey } from '@/composables/useUser'
import {
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

export type PushSubscriptionSyncState = 'disabled' | 'enabled' | 'server-only' | 'browser-only'

export function usePushNotifications(resolveServerEnabled: () => boolean = () => false) {
  const queryClient = useQueryClient()
  const authStore = useAuthStore()
  const permission = ref<NotificationPermission | 'unsupported'>(getNotificationPermission())
  const browserSubscription = ref<PushSubscription | null>(null)
  const isBrowserSubscriptionLoading = ref(false)
  const browserSubscriptionError = ref<unknown>(null)
  const operationControllers = new Set<AbortController>()
  let subscriptionRevision = 0

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
  const syncState = computed<PushSubscriptionSyncState>(() => {
    const serverEnabled = resolveServerEnabled()
    const browserEnabled = Boolean(browserSubscription.value)
    if (serverEnabled && browserEnabled) return 'enabled'
    if (serverEnabled) return 'server-only'
    if (browserEnabled) return 'browser-only'
    return 'disabled'
  })

  const refreshBrowserSubscription = async () => {
    const generation = authStore.sessionGeneration
    const revision = ++subscriptionRevision
    isBrowserSubscriptionLoading.value = true
    browserSubscriptionError.value = null
    try {
      const subscription = await getBrowserPushSubscription()
      if (authStore.sessionGeneration === generation && subscriptionRevision === revision) {
        browserSubscription.value = subscription
      }
    } catch (error) {
      if (authStore.sessionGeneration === generation && subscriptionRevision === revision) {
        browserSubscriptionError.value = error
      }
    } finally {
      if (authStore.sessionGeneration === generation && subscriptionRevision === revision) {
        isBrowserSubscriptionLoading.value = false
      }
    }
  }
  const stopSessionBoundaryListener = subscribeAuthSessionBoundary(() => {
    subscriptionRevision += 1
    operationControllers.forEach((controller) => controller.abort())
    operationControllers.clear()
    browserSubscription.value = null
    browserSubscriptionError.value = null
    isBrowserSubscriptionLoading.value = false
    void refreshBrowserSubscription()
  })
  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopSessionBoundaryListener()
      subscriptionRevision += 1
      operationControllers.forEach((controller) => controller.abort())
      operationControllers.clear()
    })
  }
  void refreshBrowserSubscription()

  const enableMutation = useMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: async () => {
      const revision = ++subscriptionRevision
      isBrowserSubscriptionLoading.value = false
      const intent = captureAuthSessionIntent(authStore)
      const controller = startSessionOperation()
      let subscription: PushSubscription | null = null
      let createdSubscription = false
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
        subscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (!subscription) {
          subscription = await subscribeBrowserPush(publicKeyQuery.data.value.publicKey)
          createdSubscription = true
        }
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await saveBrowserPushSubscription(subscription, { signal: controller.signal })
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (subscriptionRevision === revision) browserSubscription.value = subscription
      } catch (error) {
        if (subscription && createdSubscription) {
          const unsubscribed = await subscription.unsubscribe().catch(() => false)
          if (isAuthSessionIntentCurrent(authStore, intent) && subscriptionRevision === revision) {
            browserSubscription.value = unsubscribed
              ? null
              : await getBrowserPushSubscription().catch(() => subscription)
          }
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
      const revision = ++subscriptionRevision
      isBrowserSubscriptionLoading.value = false
      const intent = captureAuthSessionIntent(authStore)
      const controller = startSessionOperation()
      try {
        const subscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await userApi.deleteAllPushSubscriptions({ signal: controller.signal })
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (subscription) await subscription.unsubscribe()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        const remainingSubscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (subscriptionRevision === revision) browserSubscription.value = remainingSubscription
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
    syncState,
    hasBrowserSubscription: computed(() => Boolean(browserSubscription.value)),
    isBrowserSubscriptionLoading,
    refreshBrowserSubscription,
    permission,
    publicKey: publicKeyQuery.data,
    isLoading: computed(() => publicKeyQuery.isLoading.value || isBrowserSubscriptionLoading.value),
    isError: computed(() => publicKeyQuery.isError.value || Boolean(browserSubscriptionError.value)),
    error: computed(() => browserSubscriptionError.value ?? publicKeyQuery.error.value),
    refetch: () => Promise.all([publicKeyQuery.refetch(), refreshBrowserSubscription()]),
    isEnabling: enableMutation.isPending,
    isDisabling: disableMutation.isPending,
    enablePush: enableMutation.mutateAsync,
    disablePush: disableMutation.mutateAsync,
  }
}
