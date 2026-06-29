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
  const stripped = stripHtmlWithParser(value, tagReplacement)
  const normalized = options.collapseWhitespace
    ? stripped.replace(/\s+/g, ' ')
    : stripped

  return normalized.trim()
}

function stripHtmlWithParser(value: string, tagReplacement: string) {
  if (typeof DOMParser === 'undefined') {
    return value.replace(/<[^>]*>/g, tagReplacement)
  }

  const parser = new DOMParser()
  const doc = parser.parseFromString(value, 'text/html')
  doc.querySelectorAll('script, style, template').forEach((node) => node.remove())

  if (!tagReplacement) {
    return doc.body.textContent ?? ''
  }

  const parts: string[] = []
  const appendText = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      parts.push(node.textContent ?? '')
      return
    }

    if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) {
      return
    }

    if (node.nodeType === Node.ELEMENT_NODE) {
      parts.push(tagReplacement)
    }

    node.childNodes.forEach(appendText)

    if (node.nodeType === Node.ELEMENT_NODE) {
      parts.push(tagReplacement)
    }
  }

  doc.body.childNodes.forEach(appendText)
  return parts.join('')
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
