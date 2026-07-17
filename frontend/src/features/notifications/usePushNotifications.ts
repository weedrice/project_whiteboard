import { computed, ref } from 'vue'
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

export function usePushNotifications() {
  const queryClient = useQueryClient()
  const permission = ref<NotificationPermission | 'unsupported'>(getNotificationPermission())
  const publicKeyQuery = useQuery({
    queryKey: ['push', 'public-key'],
    queryFn: async ({ signal }) => unwrapAxiosApiData(await userApi.getPushPublicKey({ signal })),
    staleTime: QUERY_STALE_TIME.LONG,
    enabled: isPushSupported(),
  })

  const enabled = computed(() => Boolean(publicKeyQuery.data.value?.enabled && publicKeyQuery.data.value.publicKey))
  const supported = computed(() => isPushSupported())

  const enableMutation = useMutation({
    mutationFn: async () => {
      if (!enabled.value || !publicKeyQuery.data.value?.publicKey) {
        throw new Error('Web push is not configured.')
      }
      const nextPermission = await requestPushPermission()
      permission.value = nextPermission
      if (nextPermission !== 'granted') {
        throw new Error('Push permission was not granted.')
      }
      const subscription = await subscribeBrowserPush(publicKeyQuery.data.value.publicKey)
      await saveBrowserPushSubscription(subscription)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userSettingsQueryKey })
    },
  })

  const disableMutation = useMutation({
    mutationFn: async () => {
      const subscription = await getBrowserPushSubscription()
      if (!subscription) {
        return
      }
      await deleteBrowserPushSubscription(subscription)
      await subscription.unsubscribe()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userSettingsQueryKey })
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
