import type { MyReport, Report } from '@/types'

type TranslateFn = (key: string) => string

export type ReportStatus = Report['status'] | MyReport['status']
export type ReportTargetType = Report['targetType'] | MyReport['targetType']
export type ReportStatusVariant = 'warning' | 'success' | 'gray'

export function getReportStatusVariant(status: ReportStatus): ReportStatusVariant {
  if (status === 'PENDING') {
    return 'warning'
  }
  if (status === 'RESOLVED') {
    return 'success'
  }
  return 'gray'
}

export function getAdminReportStatusLabel(t: TranslateFn, status: ReportStatus) {
  return t(`admin.reports.status.${status}`)
}

export function getCommonReportTargetTypeLabel(t: TranslateFn, targetType: ReportTargetType) {
  switch (targetType) {
    case 'POST':
      return t('common.post')
    case 'COMMENT':
      return t('common.comment')
    case 'USER':
      return t('common.user')
    default:
      return targetType
  }
}

export function getMyReportTargetTypeLabel(t: TranslateFn, targetType: ReportTargetType) {
  return t(`report.types.${targetType.toLowerCase()}`)
}

export function getMyReportStatusLabel(t: TranslateFn, status: ReportStatus) {
  switch (status) {
    case 'PENDING':
      return t('user.reportList.pending')
    case 'RESOLVED':
      return t('user.reportList.processed')
    case 'REJECTED':
      return t('user.reportList.rejected')
    default:
      return status
  }
}

export function getMyReportStatusClass(status: ReportStatus) {
  switch (status) {
    case 'PENDING':
      return 'bg-yellow-100 dark:bg-yellow-900/50 text-yellow-800 dark:text-yellow-400'
    case 'RESOLVED':
      return 'bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400'
    case 'REJECTED':
      return 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-400'
    default:
      return ''
  }
}
