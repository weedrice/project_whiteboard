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

export function getReportProcessorText(t: TranslateFn, report: Pick<Report, 'adminId' | 'processorUserId'>) {
  if (report.adminId != null) {
    return `${t('common.defaultAdminName')} #${report.adminId}`
  }
  if (report.processorUserId != null) {
    return `${t('common.user')} #${report.processorUserId}`
  }
  return '-'
}

export function getReportReasonText(report: Pick<Report, 'contents' | 'remark'>) {
  return report.contents?.trim() || report.remark?.trim() || '-'
}

export function getReportTargetDisplayText(
  t: TranslateFn,
  report: Pick<Report, 'targetDisplayName' | 'targetLoginId' | 'targetType' | 'targetId'>,
) {
  if (report.targetDisplayName != null && report.targetLoginId != null) {
    return `${report.targetDisplayName}\n${report.targetLoginId}`
  }

  return `${getCommonReportTargetTypeLabel(t, report.targetType)} #${report.targetId}`
}

export function getMyReportTargetTypeLabel(t: TranslateFn, targetType: ReportTargetType) {
  return t(`report.types.${targetType.toLowerCase()}`)
}

export function getReportReasonTypeLabel(t: TranslateFn, reasonType: string) {
  if (reasonType === 'SPAM' || reasonType === 'ABUSE' || reasonType === 'ADULT' || reasonType === 'ETC') {
    return t(`report.reasonTypes.${reasonType}`)
  }
  return reasonType || '-'
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
      return 'nv-status-warning'
    case 'RESOLVED':
      return 'nv-status-success'
    case 'REJECTED':
      return 'nv-status-danger'
    default:
      return ''
  }
}
