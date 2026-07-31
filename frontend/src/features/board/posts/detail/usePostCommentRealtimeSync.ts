import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { isAxiosError } from 'axios'
import { notificationApi } from '@/api/notification'
import { onCommentStreamEvent } from '@/features/comments/commentStreamEvents'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import {
  getNotificationStreamConnection,
  subscribeNotificationStreamConnection,
  type NotificationStreamConnection,
} from '@/features/notifications/stream/notificationStreamConnectionEvents'
import { recycleNotificationStreamConnection } from '@/features/notifications/stream/notificationStreamController'
import { sessionQueryKey } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

type ActiveCommentTopic = NotificationStreamConnection & { postId: string }

interface UsePostCommentRealtimeSyncOptions {
  postId: Readonly<Ref<string>>
  commentQueryPostId: Readonly<Ref<string | number>>
  currentUserId: Readonly<Ref<number | undefined>>
  isAuthenticated: Readonly<Ref<boolean>>
  sessionGeneration: Readonly<Ref<number>>
  queryClient: QueryClient | null
}

export function usePostCommentRealtimeSync({
  postId,
  commentQueryPostId,
  currentUserId,
  isAuthenticated,
  sessionGeneration,
  queryClient,
}: UsePostCommentRealtimeSyncOptions) {
  let activeCommentTopic: ActiveCommentTopic | null = null
  let commentTopicOperation = 0
  const commentTopicAbortControllers = new Set<AbortController>()
  let commentRefreshTimer: ReturnType<typeof setTimeout> | null = null
  const pendingCommentCount = ref(0)

  function isCurrentCommentConnection(connection: NotificationStreamConnection) {
    const current = getNotificationStreamConnection()
    return isAuthenticated.value
      && sessionGeneration.value === connection.sessionGeneration
      && current?.sessionGeneration === connection.sessionGeneration
      && current.connectionId === connection.connectionId
  }

  function isUncertainTopicRegistrationError(error: unknown) {
    return isCancellationError(error) || !isAxiosError(error) || !error.response
  }

  async function syncCommentTopic(connection = getNotificationStreamConnection()) {
    const operation = ++commentTopicOperation
    if (!connection) {
      commentTopicAbortControllers.forEach((pendingController) => pendingController.abort())
      commentTopicAbortControllers.clear()
    }
    const controller = new AbortController()
    commentTopicAbortControllers.add(controller)
    const desiredPostId = postId.value

    const previous = activeCommentTopic
    activeCommentTopic = null
    if (previous && previous.sessionGeneration === sessionGeneration.value) {
      try {
        await notificationApi.unsubscribeCommentTopic(previous.postId, previous.connectionId, {
          signal: controller.signal,
          skipGlobalErrorHandler: true,
        })
      } catch {
        if (isCurrentCommentConnection(previous)) recycleNotificationStreamConnection()
      }
    }

    if (operation !== commentTopicOperation
      || controller.signal.aborted
      || !connection
      || !isCurrentCommentConnection(connection)) {
      commentTopicAbortControllers.delete(controller)
      return
    }

    try {
      await notificationApi.subscribeCommentTopic(desiredPostId, connection.connectionId, {
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      })
      if (operation !== commentTopicOperation
        || controller.signal.aborted
        || postId.value !== desiredPostId
        || !isCurrentCommentConnection(connection)) {
        if (isCurrentCommentConnection(connection)) {
          void notificationApi.unsubscribeCommentTopic(desiredPostId, connection.connectionId, {
            skipGlobalErrorHandler: true,
          }).catch(() => undefined)
        }
        return
      }
      activeCommentTopic = { ...connection, postId: desiredPostId }
    } catch (error) {
      if (isCurrentCommentConnection(connection) && isUncertainTopicRegistrationError(error)) {
        recycleNotificationStreamConnection()
      }
      // SSE comments are an enhancement; the regular comment queries keep working.
    } finally {
      commentTopicAbortControllers.delete(controller)
    }
  }

  function clearCommentRefreshTimer() {
    if (!commentRefreshTimer) return
    clearTimeout(commentRefreshTimer)
    commentRefreshTimer = null
  }

  function refreshComments(expectedSessionGeneration = sessionGeneration.value) {
    clearCommentRefreshTimer()
    if (expectedSessionGeneration !== sessionGeneration.value) return
    pendingCommentCount.value = 0
    void queryClient?.invalidateQueries({
      queryKey: sessionQueryKey(
        expectedSessionGeneration,
        commentQueryKeys.postRoot(commentQueryPostId.value),
      ),
    })
  }

  function scheduleCommentRefresh(expectedSessionGeneration: number) {
    clearCommentRefreshTimer()
    commentRefreshTimer = setTimeout(() => {
      refreshComments(expectedSessionGeneration)
    }, 2000)
  }

  const stopCommentStreamListener = onCommentStreamEvent((event) => {
    if (event.sessionGeneration !== sessionGeneration.value) return
    if (String(event.postId) !== String(postId.value)) return

    if (event.action === 'UPDATED' || event.action === 'DELETED') {
      scheduleCommentRefresh(event.sessionGeneration)
      return
    }

    if (event.action !== 'CREATED' || event.actorUserId === currentUserId.value) return
    pendingCommentCount.value += 1
  })

  const stopCommentConnectionListener = subscribeNotificationStreamConnection((connection) => {
    void syncCommentTopic(connection)
  })

  watch([postId, isAuthenticated], ([_nextPostId, authenticated]) => {
    pendingCommentCount.value = 0
    clearCommentRefreshTimer()
    if (!authenticated) {
      void syncCommentTopic(null)
      return
    }

    void syncCommentTopic()
  }, { immediate: true })

  onBeforeUnmount(() => {
    clearCommentRefreshTimer()
    stopCommentStreamListener()
    stopCommentConnectionListener()
    commentTopicOperation += 1
    commentTopicAbortControllers.forEach((controller) => controller.abort())
    commentTopicAbortControllers.clear()
    const active = activeCommentTopic
    activeCommentTopic = null
    if (active && isCurrentCommentConnection(active)) {
      void notificationApi.unsubscribeCommentTopic(active.postId, active.connectionId, {
        skipGlobalErrorHandler: true,
      }).catch(() => {
        if (isCurrentCommentConnection(active)) recycleNotificationStreamConnection()
      })
    }
  })

  return {
    pendingCommentCount,
    refreshComments,
  }
}
