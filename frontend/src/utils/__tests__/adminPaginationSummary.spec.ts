import { describe, expect, it } from 'vitest'
import { formatAdminPaginationSummary } from '../adminPaginationSummary'

describe('formatAdminPaginationSummary', () => {
  it('formats a count summary with the default item unit', () => {
    expect(formatAdminPaginationSummary(1234)).toBe('총 1,234건')
  })

  it('formats a parenthesized page summary by default when page data is provided', () => {
    expect(formatAdminPaginationSummary(1234, {
      page: 0,
      totalPages: 3,
    })).toBe('총 1,234건 (1 / 3 페이지)')
  })

  it('formats a slash page summary with a custom unit', () => {
    expect(formatAdminPaginationSummary(12, {
      unit: '명',
      page: 1,
      totalPages: 5,
      pageFormat: 'slash',
    })).toBe('총 12명 / 2 / 5 페이지')
  })

  it('can omit page details while preserving the count summary', () => {
    expect(formatAdminPaginationSummary(0, {
      unit: '명',
      page: 0,
      totalPages: 0,
      pageFormat: 'slash',
      includePage: false,
    })).toBe('총 0명')
  })
})
