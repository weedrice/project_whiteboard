const LOCAL_FILE_PATH_PATTERN = /^\/api\/v1(\/files\/[1-9]\d*(?:\/variants\/[a-z0-9-]+)?)$/i

export interface AuthenticatedFileDisposition {
  forceDownload: boolean
  fileName?: string
}

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

export function resolveAuthenticatedFileDisposition(header: unknown): AuthenticatedFileDisposition {
  if (typeof header !== 'string') return { forceDownload: false }

  const [dispositionType] = header.split(';', 1)
  if (dispositionType?.trim().toLowerCase() !== 'attachment') {
    return { forceDownload: false }
  }

  const encodedFileName = header.match(/filename\*\s*=\s*UTF-8'[^']*'([^;]+)/i)?.[1]
  const basicFileName = header.match(/filename\s*=\s*(?:"([^"]*)"|([^;]+))/i)
  const rawFileName = encodedFileName ?? basicFileName?.[1] ?? basicFileName?.[2]
  if (!rawFileName) return { forceDownload: true }

  const decodedFileName = decodeFileName(rawFileName.trim())
  const safeFileName = decodedFileName
    .replace(/[\u0000-\u001f\u007f<>:"/\\|?*]+/g, '_')
    .replace(/[. ]+$/g, '')
    .trim()
  return safeFileName
    ? { forceDownload: true, fileName: safeFileName }
    : { forceDownload: true }
}

function decodeFileName(value: string): string {
  const unquoted = value.startsWith('"') && value.endsWith('"')
    ? value.slice(1, -1)
    : value
  try {
    return decodeURIComponent(unquoted)
  } catch {
    return unquoted
  }
}

function getApplicationOrigin(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return 'https://noviis.kr'
}
