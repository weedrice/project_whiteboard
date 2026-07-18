import { onUnmounted, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useNotification } from '@/features/notifications/queries/useNotification'
import { createNotificationStreamController } from '@/features/notifications/stream/notificationStreamController'

export function useNotificationStream(
  isAuthenticated: () => boolean,
  sessionGeneration: () => number,
) {
  const queryClient = useQueryClient()
  const { useUnreadCount } = useNotification()
  const { connectToSse, closeSse } = createNotificationStreamController(queryClient)
  const { data: unreadCount } = useUnreadCount()

  watch(
    () => [isAuthenticated(), sessionGeneration()] as const,
    ([authenticated, generation], previous) => {
      if (authenticated) {
        if (previous?.[0] && previous[1] !== generation) {
          closeSse()
        }
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
