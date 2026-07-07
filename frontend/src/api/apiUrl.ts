const URL_PARSE_BASE = 'https://noviis.local'

export function getRequestPathname(url: string | undefined): string {
  if (!url) return ''

  try {
    return new URL(url, URL_PARSE_BASE).pathname
  } catch {
    return url.split(/[?#]/, 1)[0] ?? ''
  }
}
