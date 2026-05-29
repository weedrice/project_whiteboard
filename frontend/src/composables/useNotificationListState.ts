import { computed, type Ref } from 'vue'
import { useNotification } from '@/composables/useNotification'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import type { NotificationParams } from '@/api/notification'

type Translate = (key: string) => string

export function useNotificationListState(params: Ref<NotificationParams>, t?: Translate) {
  const { useNotifications } = useNotification()
  const query = useNotifications(params)

  const notifications = computed(() => query.data.value?.content ?? [])
  const totalPages = computed(() => query.data.value?.totalPages ?? 0)
  const errorMessage = computed(() => query.isError.value ? getListLoadErrorMessage(t) : '')

  return {
    ...query,
    notifications,
    totalPages,
    errorMessage
  }
}
