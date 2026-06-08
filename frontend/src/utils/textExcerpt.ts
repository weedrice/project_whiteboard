export interface StripHtmlToTextOptions {
  tagReplacement?: string
  collapseWhitespace?: boolean
}

export interface TruncateWithEllipsisOptions {
  ellipsisAtLength?: boolean
}

export function stripHtmlToText(
  value: string | null | undefined,
  options: StripHtmlToTextOptions = {},
) {
  if (!value) return ''

  const tagReplacement = options.tagReplacement ?? ''
  const stripped = value.replace(/<[^>]*>/g, tagReplacement)
  const normalized = options.collapseWhitespace
    ? stripped.replace(/\s+/g, ' ')
    : stripped

  return normalized.trim()
}

export function truncateWithEllipsis(
  value: string,
  maxLength: number,
  options: TruncateWithEllipsisOptions = {},
) {
  const shouldTruncate = value.length > maxLength
    || (options.ellipsisAtLength && value.length === maxLength)

  if (!shouldTruncate) return value
  return `${value.slice(0, maxLength)}...`
}
