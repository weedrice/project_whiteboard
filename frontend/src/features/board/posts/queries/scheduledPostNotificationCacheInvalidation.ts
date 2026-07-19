import type { QueryClient } from '@tanstack/vue-query'
import type { Notification } from '@/types'
import { invalidatePostCaches } from '@/features/board/posts/queries/postCacheUpdates'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

const SCHEDULED_PUBLISHED_KEY = 'notification.scheduled.published'
const SCHEDULED_FAILED_KEY = 'notification.scheduled.failed'

export function invalidateScheduledPostNotificationCaches(
  queryClient: QueryClient,
  notification: Notification,
  sessionGeneration: number,
) {
  if (notification.messageKey !== SCHEDULED_PUBLISHED_KEY
    && notification.messageKey !== SCHEDULED_FAILED_KEY) return

  const sessionKey = (queryKey: readonly unknown[]) => sessionQueryKey(sessionGeneration, queryKey)
  queryClient.invalidateQueries({ queryKey: sessionKey(userQueryKeys.scheduledPostsRoot) })

  if (notification.messageKey === SCHEDULED_FAILED_KEY) {
    queryClient.invalidateQueries({
      queryKey: sessionKey(userQueryKeys.scheduledPost(notification.sourceId)),
    })
    return
  }

  queryClient.invalidateQueries({ queryKey: sessionKey(userQueryKeys.draftsRoot) })
  queryClient.invalidateQueries({ queryKey: sessionKey(userQueryKeys.pointsRoot) })
  invalidatePostCaches(queryClient, sessionGeneration, notification.sourceId)
}
