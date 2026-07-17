export const PUSH_NOTIFICATION_FALLBACK_PATH = '/mypage/notifications'

export function resolveInternalPushNotificationUrl(
  candidate: unknown,
  origin: string,
  fallbackPath = PUSH_NOTIFICATION_FALLBACK_PATH,
): string {
  const fallback = new URL(fallbackPath, origin).href
  const hasControlCharacter = typeof candidate === 'string'
    && [...candidate].some((character) => {
      const code = character.charCodeAt(0)
      return code <= 31 || code === 127
    })
  if (typeof candidate !== 'string'
    || !candidate.startsWith('/')
    || candidate.startsWith('//')
    || candidate.includes('\\')
    || hasControlCharacter) {
    return fallback
  }

  try {
    const resolved = new URL(candidate, origin)
    return resolved.origin === origin && (resolved.protocol === 'https:' || resolved.protocol === 'http:')
      ? resolved.href
      : fallback
  } catch {
    return fallback
  }
}
