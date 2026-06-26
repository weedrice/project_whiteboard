import { formatInteger } from '@/utils/numberFormat'

export type AdminPaginationSummaryPageFormat = 'parenthesized' | 'slash'
type Translate = (key: string, params?: Record<string, unknown>) => string

type AdminPaginationSummaryOptions = {
  unit?: string
  page?: number
  totalPages?: number
  pageFormat?: AdminPaginationSummaryPageFormat
  includePage?: boolean
  t?: Translate
}

export function formatAdminPaginationSummary(
  totalElements: number,
  options: AdminPaginationSummaryOptions = {},
) {
  const t = options.t
  const unit = options.unit ?? t?.('common.paginationSummary.itemUnit') ?? ''
  const summary = t
    ? t('common.paginationSummary.total', { count: formatInteger(totalElements), unit })
    : `${formatInteger(totalElements)}${unit}`
  const includePage = options.includePage ?? (
    options.page !== undefined
    && options.totalPages !== undefined
  )

  if (!includePage || options.page === undefined || options.totalPages === undefined) {
    return summary
  }

  const currentPage = options.page + 1
  const pageLabel = t?.('common.paginationSummary.page') ?? ''
  if (options.pageFormat === 'slash') {
    return t
      ? t('common.paginationSummary.slash', { summary, currentPage, totalPages: options.totalPages, pageLabel })
      : `${summary} / ${currentPage} / ${options.totalPages}`
  }

  return t
    ? t('common.paginationSummary.parenthesized', { summary, currentPage, totalPages: options.totalPages, pageLabel })
    : `${summary} (${currentPage} / ${options.totalPages})`
}
