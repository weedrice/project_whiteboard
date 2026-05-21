import { onUnmounted, watch } from 'vue'
import { useNotification } from '@/composables/useNotification'

export function useNotificationStream(isAuthenticated: () => boolean) {
  const { useUnreadCount, connectToSse, closeSse } = useNotification()
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
