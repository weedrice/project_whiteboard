import { describe, expect, it } from 'vitest'
import {
  getAdminReportStatusLabel,
  getCommonReportTargetTypeLabel,
  getMyReportStatusClass,
  getMyReportStatusLabel,
  getMyReportTargetTypeLabel,
  getReportProcessorText,
  getReportReasonTypeLabel,
  getReportReasonText,
  getReportStatusVariant,
  getReportTargetDisplayText
} from '@/utils/reportDisplay'

const t = (key: string) => `translated:${key}`

describe('reportDisplay', () => {
  it('maps report status to badge variants', () => {
    expect(getReportStatusVariant('PENDING')).toBe('warning')
    expect(getReportStatusVariant('RESOLVED')).toBe('success')
    expect(getReportStatusVariant('REJECTED')).toBe('gray')
  })

  it('keeps admin status labels in the admin namespace', () => {
    expect(getAdminReportStatusLabel(t, 'PENDING')).toBe('translated:admin.reports.status.PENDING')
    expect(getAdminReportStatusLabel(t, 'RESOLVED')).toBe('translated:admin.reports.status.RESOLVED')
  })

  it('maps report target type labels for common admin display', () => {
    expect(getCommonReportTargetTypeLabel(t, 'POST')).toBe('translated:common.post')
    expect(getCommonReportTargetTypeLabel(t, 'COMMENT')).toBe('translated:common.comment')
    expect(getCommonReportTargetTypeLabel(t, 'USER')).toBe('translated:common.user')
  })

  it('keeps my-report labels in the user-facing namespaces', () => {
    expect(getMyReportTargetTypeLabel(t, 'POST')).toBe('translated:report.types.post')
    expect(getMyReportStatusLabel(t, 'PENDING')).toBe('translated:user.reportList.pending')
    expect(getMyReportStatusLabel(t, 'RESOLVED')).toBe('translated:user.reportList.processed')
    expect(getMyReportStatusLabel(t, 'REJECTED')).toBe('translated:user.reportList.rejected')
    expect(getReportReasonTypeLabel(t, 'SPAM')).toBe('translated:report.reasonTypes.SPAM')
    expect(getReportReasonTypeLabel(t, 'LEGACY')).toBe('LEGACY')
  })

  it('maps my-report status classes', () => {
    expect(getMyReportStatusClass('PENDING')).toBe('nv-status-warning')
    expect(getMyReportStatusClass('RESOLVED')).toBe('nv-status-success')
    expect(getMyReportStatusClass('REJECTED')).toBe('nv-status-danger')
  })

  it('formats admin report processor, reason, and target display text', () => {
    expect(getReportProcessorText(t, { adminId: 7, processorUserId: null })).toBe('translated:common.defaultAdminName #7')
    expect(getReportProcessorText(t, { adminId: null, processorUserId: 9 })).toBe('translated:common.user #9')
    expect(getReportProcessorText(t, { adminId: null, processorUserId: null })).toBe('-')

    expect(getReportReasonText({ contents: '  abuse  ', remark: 'fallback' })).toBe('abuse')
    expect(getReportReasonText({ contents: ' ', remark: '  fallback  ' })).toBe('fallback')
    expect(getReportReasonText({ contents: null, remark: null })).toBe('-')

    expect(getReportTargetDisplayText(t, {
      targetDisplayName: 'Target',
      targetLoginId: 'target-id',
      targetType: 'USER',
      targetId: 3,
    })).toBe('Target\ntarget-id')
    expect(getReportTargetDisplayText(t, {
      targetDisplayName: null,
      targetLoginId: null,
      targetType: 'POST',
      targetId: 4,
    })).toBe('translated:common.post #4')
  })
})
