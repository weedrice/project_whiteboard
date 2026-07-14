import type { PageResponse } from '@/types'

export interface PageResponseRaw<T> {
  content?: T[] | null
  page?: number
  number?: number
  size?: number
  totalElements?: number
  totalPages?: number
  hasNext?: boolean
  hasPrevious?: boolean
  last?: boolean
  first?: boolean
  empty?: boolean
}

interface NormalizePageResponseOptions {
  fallbackNumber?: number
  fallbackTotalPages?: number | ((context: { size: number; totalElements: number; contentLength: number }) => number)
}

export function normalizePageResponse<T>(
  raw: PageResponseRaw<T> | null | undefined,
  options: NormalizePageResponseOptions = {}
): PageResponse<T> {
  const content = raw?.content ?? []
  const number = typeof raw?.number === 'number'
    ? raw.number
    : (typeof raw?.page === 'number' ? raw.page : (options.fallbackNumber ?? 0))
  const size = typeof raw?.size === 'number' ? raw.size : content.length
  const totalElements = typeof raw?.totalElements === 'number' ? raw.totalElements : content.length
  const fallbackTotalPages = typeof options.fallbackTotalPages === 'function'
    ? options.fallbackTotalPages({ size, totalElements, contentLength: content.length })
    : options.fallbackTotalPages
  const totalPages = typeof raw?.totalPages === 'number'
    ? raw.totalPages
    : (fallbackTotalPages ?? 1)
  const first = typeof raw?.first === 'boolean' ? raw.first : number <= 0
  const last = typeof raw?.last === 'boolean'
    ? raw.last
    : (typeof raw?.hasNext === 'boolean' ? !raw.hasNext : totalPages <= 1 || number >= totalPages - 1)
  const empty = typeof raw?.empty === 'boolean' ? raw.empty : content.length === 0

  return {
    content,
    totalElements,
    totalPages,
    size,
    number,
    first,
    last,
    empty,
  }
}

export function normalizePageResponseItems<TSource, TTarget>(
  raw: PageResponseRaw<TSource> | null | undefined,
  mapItem: (item: TSource) => TTarget,
  options: NormalizePageResponseOptions = {}
): PageResponse<TTarget> {
  const page = normalizePageResponse(raw, options)
  return {
    ...page,
    content: page.content.map(mapItem),
  }
}
