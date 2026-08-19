import { onUnmounted, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { createNotificationStreamController } from '@/features/notifications/stream/notificationStreamController'

export function useNotificationStream(
  isAuthenticated: () => boolean,
  sessionGeneration: () => number,
) {
  const queryClient = useQueryClient()
  const { connectToSse, closeSse } = createNotificationStreamController(queryClient)

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

}
