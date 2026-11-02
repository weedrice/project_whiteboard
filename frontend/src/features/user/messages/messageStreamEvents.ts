import type { Notification } from '@/types'

export interface MessageChangedEvent {
    messageId: number
    partnerId: number
}

export type MailboxStreamEvent = Notification | MessageChangedEvent
type MessageStreamListener = (event: MailboxStreamEvent) => void

const listeners = new Set<MessageStreamListener>()

export function emitMessageStreamEvent(notification: Notification) {
    if (notification.notificationType !== 'MESSAGE') return
    listeners.forEach((listener) => listener(notification))
}

export function emitMessageChangedEvent(event: MessageChangedEvent) {
    listeners.forEach((listener) => listener(event))
}

export function subscribeMessageStreamEvents(listener: MessageStreamListener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
}
