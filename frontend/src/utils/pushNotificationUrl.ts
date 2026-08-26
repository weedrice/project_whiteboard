export const PUSH_NOTIFICATION_FALLBACK_PATH = '/mypage/notifications'
export const PUSH_NOTIFICATION_IMAGE_FALLBACK_PATH = '/pwa-192x192.png'

const SPA_EXACT_PATHS = new Set([
  '/',
  '/boards',
  '/inquiry',
  '/inquiries',
  '/admin/inquiries',
  '/mypage',
  '/emoticons',
  '/search',
  '/privacy',
  '/terms',
])
const SPA_PATH_PREFIXES = ['/board/', '/user/', '/mypage/', '/emoticons/', '/tag/', '/inquiries/', '/admin/inquiries/']
const IMAGE_PATH_PREFIXES = ['/assets/', '/img/', '/icons/', '/uploads/']
const IMAGE_FILE_PATTERN = /\.(?:avif|gif|jpe?g|png|svg|webp)$/i

function decodePathSafely(pathname: string): string | null {
  let decoded = pathname
  for (let index = 0; index < 3; index += 1) {
    try {
      const next = decodeURIComponent(decoded)
      if (next === decoded) return next
      decoded = next
    } catch {
      return null
    }
  }
  return decoded.includes('%') ? null : decoded
}

function resolveSameOriginRelativePath(candidate: unknown, origin: string): URL | null {
  if (typeof candidate !== 'string'
    || !candidate.startsWith('/')
    || candidate.startsWith('//')
    || candidate.includes('\\')
    || [...candidate].some((character) => {
      const code = character.charCodeAt(0)
      return code <= 31 || code === 127
    })) return null

  try {
    const resolved = new URL(candidate, origin)
    if (resolved.origin !== origin || !['http:', 'https:'].includes(resolved.protocol)) return null
    const decodedPath = decodePathSafely(resolved.pathname)
    if (!decodedPath || decodedPath.includes('\\') || decodedPath.startsWith('//')) return null
    resolved.pathname = decodedPath
    return resolved
  } catch {
    return null
  }
}

export function resolvePushNavigationUrl(candidate: unknown, origin: string): string {
  const fallback = new URL(PUSH_NOTIFICATION_FALLBACK_PATH, origin).href
  const resolved = resolveSameOriginRelativePath(candidate, origin)
  if (!resolved) return fallback
  const path = resolved.pathname.replace(/\/+$/, '') || '/'
  return SPA_EXACT_PATHS.has(path) || SPA_PATH_PREFIXES.some((prefix) => path.startsWith(prefix))
    ? resolved.href
    : fallback
}

export function resolvePushImageUrl(candidate: unknown, origin: string): string {
  const fallback = new URL(PUSH_NOTIFICATION_IMAGE_FALLBACK_PATH, origin).href
  const resolved = resolveSameOriginRelativePath(candidate, origin)
  if (!resolved) return fallback
  const isBundledPwaImage = /^\/pwa-[0-9]+x[0-9]+\.png$/i.test(resolved.pathname)
  const isAllowedAsset = IMAGE_PATH_PREFIXES.some((prefix) => resolved.pathname.startsWith(prefix))
    && IMAGE_FILE_PATTERN.test(resolved.pathname)
  return isBundledPwaImage || isAllowedAsset ? resolved.href : fallback
}

/** @deprecated Use the purpose-specific navigation or image resolver. */
export function resolveInternalPushNotificationUrl(
  candidate: unknown,
  origin: string,
  fallbackPath = PUSH_NOTIFICATION_FALLBACK_PATH,
): string {
  return fallbackPath === PUSH_NOTIFICATION_IMAGE_FALLBACK_PATH
    ? resolvePushImageUrl(candidate, origin)
    : resolvePushNavigationUrl(candidate, origin)
}
