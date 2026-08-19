const LOCAL_FILE_PATH_PATTERN = /^\/api\/v1(\/files\/[1-9]\d*(?:\/variants\/[a-z0-9-]+)?)$/i

export function resolveAuthenticatedFileRequestPath(
  source: string | null | undefined,
  applicationOrigin = getApplicationOrigin(),
): string | null {
  if (!source || source.startsWith('//')) return null

  try {
    const origin = new URL(applicationOrigin).origin
    const url = new URL(source, origin)
    if (url.origin !== origin) return null

    const match = url.pathname.match(LOCAL_FILE_PATH_PATTERN)
    return match ? `${match[1]}${url.search}` : null
  } catch {
    return null
  }
}

function getApplicationOrigin(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return 'https://noviis.kr'
}
