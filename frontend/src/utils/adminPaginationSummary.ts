export type AdminPaginationSummaryPageFormat = 'parenthesized' | 'slash'

type AdminPaginationSummaryOptions = {
  unit?: string
  page?: number
  totalPages?: number
  pageFormat?: AdminPaginationSummaryPageFormat
  includePage?: boolean
}

export function formatAdminPaginationSummary(
  totalElements: number,
  options: AdminPaginationSummaryOptions = {},
) {
  const unit = options.unit ?? '건'
  const summary = `총 ${totalElements.toLocaleString()}${unit}`
  const includePage = options.includePage ?? (
    options.page !== undefined
    && options.totalPages !== undefined
  )

  if (!includePage || options.page === undefined || options.totalPages === undefined) {
    return summary
  }

  const currentPage = options.page + 1
  if (options.pageFormat === 'slash') {
    return `${summary} / ${currentPage} / ${options.totalPages} 페이지`
  }

  return `${summary} (${currentPage} / ${options.totalPages} 페이지)`
}
