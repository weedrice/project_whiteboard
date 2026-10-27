import type { QueryClient } from '@tanstack/vue-query'
import { shallowReactive } from 'vue'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import type { AuthSessionIntentSource } from '@/utils/authSessionIntent'

// A comment can be mounted in the best list, main list and reply branches at
// once. Keep its lock with the query client, independently of those observers.
const pendingByClient = new WeakMap<QueryClient, Map<string, symbol>>()

function pendingFor(client: QueryClient) {
    let pending = pendingByClient.get(client)
    if (!pending) {
        pending = shallowReactive(new Map<string, symbol>())
        pendingByClient.set(client, pending)
    }
    return pending
}

function keyFor(source: AuthSessionIntentSource, commentId: string | number) {
    return JSON.stringify([source.sessionGeneration, source.user?.userId ?? null, String(commentId)])
}

export function isCommentLikePending(client: QueryClient, source: AuthSessionIntentSource, commentId: string | number) {
    return pendingFor(client).has(keyFor(source, commentId))
}

export function acquireCommentLikePending(client: QueryClient, source: AuthSessionIntentSource, commentId: string | number) {
    const pending = pendingFor(client)
    const key = keyFor(source, commentId)
    if (pending.has(key)) return undefined

    const owner = Symbol()
    pending.set(key, owner)
    const release = () => {
        if (pending.get(key) === owner) pending.delete(key)
        unsubscribe()
    }
    // Offline mutations need not settle immediately when the session ends.
    // Release their UI lock now; their own auth scope still prevents replay.
    const unsubscribe = subscribeAuthSessionBoundary(release)
    return release
}
