export interface PushNotificationPayload {
  title?: string
  body?: string
  icon?: string
  badge?: string
  url?: string
  tag?: string
}

export function normalizePushNotificationPayload(value: unknown): PushNotificationPayload {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  const source = value as Record<string, unknown>
  const payload: PushNotificationPayload = {}
  for (const key of ['title', 'body', 'icon', 'badge', 'url', 'tag'] as const) {
    if (typeof source[key] === 'string') payload[key] = source[key]
  }
  return payload
}
