import type { PageResponse } from '@/types'
import logger from '@/utils/logger'

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

/** 페이지 번호 필드가 통째로 빠진 응답을 개발 모드에서만 알린다. */
function warnOnMissingPageNumber(raw: unknown): void {
  if (!import.meta.env.DEV) return
  if (raw == null || typeof raw !== 'object') return

  const candidate = raw as { page?: unknown; number?: unknown; content?: unknown }
  if (!Array.isArray(candidate.content)) return
  if (typeof candidate.page === 'number' || typeof candidate.number === 'number') return

  logger.warn(
    '[pageResponse] 페이지 응답에 page/number 필드가 없어 0페이지로 처리한다. '
    + '백엔드 PageResponse 필드 이름이 바뀌었는지 확인할 것.',
    raw,
  )
}

/**
 * 백엔드 `PageResponse`(page/hasNext/hasPrevious)를 프론트 내부 형태(number/first/last)로 옮긴다.
 *
 * 모든 필드가 optional이라 어긋난 응답도 기본값으로 조용히 채워진다. 계약 테스트가 1차
 * 방어선이지만, 그것이 놓친 경우를 개발 중에 알아채도록 페이지 번호 필드가 통째로 없을 때만
 * 경고한다. 빈 목록 응답은 정상이므로 content가 비어 있는 경우는 대상이 아니다.
 */
export function normalizePageResponse<T>(
  raw: PageResponseRaw<T> | null | undefined,
  options: NormalizePageResponseOptions = {}
): PageResponse<T> {
  warnOnMissingPageNumber(raw)
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
