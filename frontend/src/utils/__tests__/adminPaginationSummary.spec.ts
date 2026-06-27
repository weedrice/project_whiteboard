import { describe, expect, it } from 'vitest'
import { formatAdminPaginationSummary } from '../adminPaginationSummary'

const t = (key: string, params?: Record<string, unknown>) => {
  if (key === 'common.paginationSummary.itemUnit') return '건'
  if (key === 'common.paginationSummary.page') return '페이지'
  if (key === 'common.paginationSummary.total') return `총 ${params?.count}${params?.unit}`
  if (key === 'common.paginationSummary.slash') {
    return `${params?.summary} / ${params?.currentPage} / ${params?.totalPages} ${params?.pageLabel}`
  }
  if (key === 'common.paginationSummary.parenthesized') {
    return `${params?.summary} (${params?.currentPage} / ${params?.totalPages} ${params?.pageLabel})`
  }
  return key
}

describe('formatAdminPaginationSummary', () => {
  it('formats a count summary with the default item unit', () => {
    expect(formatAdminPaginationSummary(1234, { t })).toBe('총 1,234건')
  })

  it('formats a parenthesized page summary by default when page data is provided', () => {
    expect(formatAdminPaginationSummary(1234, {
      page: 0,
      totalPages: 3,
      t,
    })).toBe('총 1,234건 (1 / 3 페이지)')
  })

  it('formats a slash page summary with a custom unit', () => {
    expect(formatAdminPaginationSummary(12, {
      unit: '명',
      page: 1,
      totalPages: 5,
      pageFormat: 'slash',
      t,
    })).toBe('총 12명 / 2 / 5 페이지')
  })

  it('can omit page details while preserving the count summary', () => {
    expect(formatAdminPaginationSummary(0, {
      unit: '명',
      page: 0,
      totalPages: 0,
      pageFormat: 'slash',
      includePage: false,
      t,
    })).toBe('총 0명')
  })
})
