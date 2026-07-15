import type { Notification, PageResponse } from '@/types'

export function isNotificationPage(data: unknown): data is PageResponse<Notification> {
  if (!data || typeof data !== 'object') return false
  const candidate = data as Partial<PageResponse<Notification>>
  return Array.isArray(candidate.content)
}

export function getNotificationPageNumber(data: unknown): number {
  const candidate = data as { number?: unknown; page?: unknown }
  if (typeof candidate.number === 'number') return candidate.number
  if (typeof candidate.page === 'number') return candidate.page
  return 0
}

export function getNotificationReconnectDelay(
  baseDelayMs: number,
  reconnectAttempt: number,
  maxDelayMs: number,
): number {
  const multiplier = 2 ** reconnectAttempt
  return Math.min(baseDelayMs * multiplier, maxDelayMs)
}

export function shouldStopNotificationReconnectAfterRefresh(status: number | null): boolean {
  return status === 401 || status === 403
}
