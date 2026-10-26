import { computed, getCurrentScope, onScopeDispose, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userSettingsQueryKey } from '@/features/user/useUser'
import {
  deleteBrowserPushSubscription,
  getBrowserPushSubscription,
  getNotificationPermission,
  isPushSupported,
  isPushSubscriptionForApplicationServerKey,
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
  const browserSubscriptionRegistered = ref(false)
  const isBrowserSubscriptionLoading = ref(false)
  const browserSubscriptionError = ref<unknown>(null)
  const operationControllers = new Set<AbortController>()
  let subscriptionRevision = 0
  let membershipRevision = 0

  watch(resolveServerEnabled, (serverEnabled) => {
    if (serverEnabled) return
    // A newer account-wide removal invalidates cached and in-flight membership
    // reads, without cancelling a registration the user is currently repairing.
    membershipRevision += 1
    browserSubscriptionRegistered.value = false
  }, { flush: 'sync' })

  const startSessionOperation = () => {
    const controller = new AbortController()
    operationControllers.add(controller)
    return controller
  }
  const invalidatePushSettings = () => queryClient.invalidateQueries({
    queryKey: currentSessionQueryKey(authStore, userSettingsQueryKey),
  })
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
    const browserEnabled = Boolean(
      browserSubscription.value
      && publicKeyQuery.data.value?.publicKey
      && isPushSubscriptionForApplicationServerKey(
        browserSubscription.value,
        publicKeyQuery.data.value.publicKey,
      ),
    )
    if (serverEnabled && browserEnabled && browserSubscriptionRegistered.value) return 'enabled'
    if (browserEnabled) return 'browser-only'
    if (serverEnabled) return 'server-only'
    return 'disabled'
  })

  const refreshBrowserSubscription = async () => {
    const intent = captureAuthSessionIntent(authStore)
    const controller = startSessionOperation()
    const revision = ++subscriptionRevision
    const membershipAtStart = membershipRevision
    const isCurrent = () => subscriptionRevision === revision
      && !controller.signal.aborted && isAuthSessionIntentCurrent(authStore, intent)
    isBrowserSubscriptionLoading.value = true
    browserSubscriptionError.value = null
    browserSubscriptionRegistered.value = false
    try {
      const subscription = await getBrowserPushSubscription()
      if (!isCurrent()) return
      browserSubscription.value = subscription
      if (subscription && intent.userId != null) {
        const registered = unwrapAxiosApiData(await userApi.getPushSubscriptionStatus(subscription.endpoint, {
          expectedSession: intent,
          signal: controller.signal,
          skipGlobalErrorHandler: true,
        }))
        if (isCurrent() && membershipAtStart === membershipRevision) {
          browserSubscriptionRegistered.value = registered === true
        }
      }
    } catch (error) {
      if (isCurrent()) browserSubscriptionError.value = error
    } finally {
      operationControllers.delete(controller)
      if (isCurrent()) isBrowserSubscriptionLoading.value = false
    }
  }
  const stopSessionBoundaryListener = subscribeAuthSessionBoundary(() => {
    subscriptionRevision += 1
    operationControllers.forEach((controller) => controller.abort())
    operationControllers.clear()
    browserSubscription.value = null
    browserSubscriptionRegistered.value = false
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

  const enableMutation = useAuthScopedMutation({
    onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
    mutationFn: async (_variables: void, mutationContext) => {
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
        if (subscription && !isPushSubscriptionForApplicationServerKey(
          subscription,
          publicKeyQuery.data.value.publicKey,
        )) {
          await deleteBrowserPushSubscription(subscription, mutationContext.withRequestConfig({ signal: controller.signal }))
          throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
          if (subscriptionRevision === revision) browserSubscriptionRegistered.value = false
          void invalidatePushSettings()
          const unsubscribed = await subscription.unsubscribe()
          throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
          if (!unsubscribed) throw new Error('The existing push subscription could not be replaced.')
          subscription = null
        }
        if (!subscription) {
          subscription = await subscribeBrowserPush(publicKeyQuery.data.value.publicKey)
          createdSubscription = true
        }
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await saveBrowserPushSubscription(subscription, mutationContext.withRequestConfig({ signal: controller.signal }))
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (subscriptionRevision === revision) {
          browserSubscription.value = subscription
          browserSubscriptionRegistered.value = true
        }
      } catch (error) {
        if (subscription && createdSubscription) {
          const unsubscribed = await subscription.unsubscribe().catch(() => false)
          if (isAuthSessionIntentCurrent(authStore, intent) && subscriptionRevision === revision) {
            const remainingSubscription = unsubscribed
              ? null
              : await getBrowserPushSubscription().catch(() => subscription)
            if (isAuthSessionIntentCurrent(authStore, intent) && subscriptionRevision === revision) {
              browserSubscriptionRegistered.value = false
              browserSubscription.value = remainingSubscription
            }
          }
        }
        throw error
      } finally {
        operationControllers.delete(controller)
      }
    },
    onSuccess: (_data, _variables, context) => {
      if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
      void invalidatePushSettings()
    },
  })

  const disableMutation = useAuthScopedMutation({
    mutationFn: async (_variables: void, mutationContext) => {
      const revision = ++subscriptionRevision
      isBrowserSubscriptionLoading.value = false
      const intent = captureAuthSessionIntent(authStore)
      const controller = startSessionOperation()
      try {
        const subscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        await userApi.deleteAllPushSubscriptions(mutationContext.withRequestConfig({ signal: controller.signal }))
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        // Server removal is complete even if browser cleanup fails afterward.
        if (subscriptionRevision === revision) browserSubscriptionRegistered.value = false
        void invalidatePushSettings()
        if (subscription) await subscription.unsubscribe()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        const remainingSubscription = await getBrowserPushSubscription()
        throwIfAuthSessionIntentChanged(authStore, intent, controller.signal)
        if (subscriptionRevision === revision) {
          browserSubscription.value = remainingSubscription
          browserSubscriptionRegistered.value = false
        }
      } finally {
        operationControllers.delete(controller)
      }
    },
  })

  return {
    enabled,
    supported,
    syncState,
    hasBrowserSubscription: computed(() => Boolean(
      browserSubscription.value
      && publicKeyQuery.data.value?.publicKey
      && isPushSubscriptionForApplicationServerKey(
        browserSubscription.value,
        publicKeyQuery.data.value.publicKey,
      ),
    )),
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
