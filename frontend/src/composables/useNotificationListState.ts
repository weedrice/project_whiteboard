import { computed, type Ref } from 'vue'
import { useNotification } from '@/composables/useNotification'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import type { NotificationParams } from '@/api/notification'

type Translate = (key: string) => string

export function useNotificationListState(params: Ref<NotificationParams>, t?: Translate) {
  const { useNotifications } = useNotification()
  const query = useNotifications(params)

  const fallbackPage = computed(() => params.value.page ?? 0)
  const { items: notifications, totalPages } = usePageResponseState(query.data, fallbackPage)
  const errorMessage = computed(() => query.isError.value ? getListLoadErrorMessage(t) : '')

  return {
    ...query,
    notifications,
    totalPages,
    errorMessage
  }
}
