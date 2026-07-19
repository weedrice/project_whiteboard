import type { Notification } from '@/types'

type MessageStreamListener = (notification: Notification) => void

const listeners = new Set<MessageStreamListener>()

export function emitMessageStreamEvent(notification: Notification) {
    if (notification.notificationType !== 'MESSAGE') return
    listeners.forEach((listener) => listener(notification))
}

export function subscribeMessageStreamEvents(listener: MessageStreamListener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
}
