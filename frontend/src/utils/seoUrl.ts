export const SEO_SITE_ORIGIN = 'https://noviis.kr'

const trailingSlashPublicPath = /^\/board\/[^/]+(?:\/post\/\d+)?\/?$/

export function normalizeSeoPath(path: string): string {
  const pathname = path.split(/[?#]/, 1)[0] || '/'
  if (pathname === '/') return '/'

  const withoutTrailingSlash = pathname.replace(/\/+$/, '') || '/'
  if (withoutTrailingSlash === '/boards' || trailingSlashPublicPath.test(withoutTrailingSlash)) {
    return `${withoutTrailingSlash}/`
  }

  return withoutTrailingSlash
}

export function buildCanonicalUrl(path: string): string {
  return `${SEO_SITE_ORIGIN}${normalizeSeoPath(path)}`
}
