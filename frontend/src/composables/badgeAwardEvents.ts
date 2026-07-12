import type { Notification } from '@/types'

type BadgeAwardListener = (notification: Notification) => void
const listeners = new Set<BadgeAwardListener>()

export function emitBadgeAwardEvent(notification: Notification) {
  if (notification.notificationType !== 'BADGE') return
  listeners.forEach((listener) => listener(notification))
}

export function subscribeBadgeAwardEvents(listener: BadgeAwardListener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
