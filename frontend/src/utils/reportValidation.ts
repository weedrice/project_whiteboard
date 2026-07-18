export const REPORT_REASON_MAX_LENGTH = 1000

export function isReportReasonTooLong(reason: string) {
  return reason.trim().length > REPORT_REASON_MAX_LENGTH
}

export function isValidReportReason(reason: string) {
  const trimmedReason = reason.trim()
  return trimmedReason.length > 0 && trimmedReason.length <= REPORT_REASON_MAX_LENGTH
}
