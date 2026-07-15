import { onUnmounted, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useNotification } from '@/composables/useNotification'
import { createNotificationStreamController } from '@/composables/notificationStreamController'

export function useNotificationStream(isAuthenticated: () => boolean) {
  const queryClient = useQueryClient()
  const { useUnreadCount } = useNotification()
  const { connectToSse, closeSse } = createNotificationStreamController(queryClient)
  const { data: unreadCount } = useUnreadCount()

  watch(
    isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        connectToSse()
        return
      }

      closeSse()
    },
    { immediate: true }
  )

  onUnmounted(() => {
    closeSse()
  })

  return {
    unreadCount
  }
}
