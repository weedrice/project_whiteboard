import { computed, type Ref } from 'vue'
import { useNotification } from '@/composables/useNotification'
import type { NotificationParams } from '@/api/notification'

export function useNotificationListState(params: Ref<NotificationParams>) {
  const { useNotifications } = useNotification()
  const query = useNotifications(params)

  const notifications = computed(() => query.data.value?.content ?? [])
  const totalPages = computed(() => query.data.value?.totalPages ?? 0)

  return {
    ...query,
    notifications,
    totalPages
  }
}
