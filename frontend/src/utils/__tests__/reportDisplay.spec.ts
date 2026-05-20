import { describe, expect, it } from 'vitest'
import {
  getAdminReportStatusLabel,
  getCommonReportTargetTypeLabel,
  getMyReportStatusClass,
  getMyReportStatusLabel,
  getMyReportTargetTypeLabel,
  getReportStatusVariant
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
  })

  it('maps my-report status classes', () => {
    expect(getMyReportStatusClass('PENDING')).toContain('bg-yellow-100')
    expect(getMyReportStatusClass('RESOLVED')).toContain('bg-green-100')
    expect(getMyReportStatusClass('REJECTED')).toContain('bg-red-100')
  })
})
