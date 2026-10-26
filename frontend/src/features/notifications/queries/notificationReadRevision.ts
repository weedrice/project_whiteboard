import type { QueryClient } from '@tanstack/vue-query'
import type { ApiQueryContext } from '@/composables/useApiQuery'
import { getAuthQuerySource } from '@/utils/authScopedQuery'
import { captureAuthSessionIntent, throwIfAuthSessionIntentChanged } from '@/utils/authSessionIntent'

const revisions = new WeakMap<QueryClient, Map<number, number>>()

function readRevision(client: QueryClient, generation: number) {
  return revisions.get(client)?.get(generation) ?? 0
}

export function advanceNotificationReadRevision(client: QueryClient, generation: number) {
  let sessionRevisions = revisions.get(client)
  if (!sessionRevisions) {
    sessionRevisions = new Map()
    revisions.set(client, sessionRevisions)
  }
  sessionRevisions.set(generation, readRevision(client, generation) + 1)
}

export async function readNotificationsAfterLatestEvent<T>(
  client: QueryClient,
  context: ApiQueryContext,
  request: () => Promise<T>,
): Promise<T> {
  const source = getAuthQuerySource()
  const intent = context.requestConfig.expectedSession ?? captureAuthSessionIntent(source)
  // SSE delivery follows the server transaction commit. A GET started before
  // that event may still contain an older snapshot, including on initial load.
  // Keep the same query pending and read again rather than cancelling an empty
  // query or replaying unread events over a later mark-as-read result.
  // During sustained events an existing SSE-updated cache stays visible. An
  // initial read waits for a stable snapshot; every request remains cancellable.
  for (;;) {
    throwIfAuthSessionIntentChanged(source, intent, context.signal)
    const revision = readRevision(client, intent.sessionGeneration)
    const response = await request()
    throwIfAuthSessionIntentChanged(source, intent, context.signal)
    if (revision === readRevision(client, intent.sessionGeneration)) return response
  }
}
