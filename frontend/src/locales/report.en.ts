import type { ReportMessages } from './types'
export const reportEn: ReportMessages = {
  title: 'Report user',
  target: 'Report target',
  reason: 'Report reason',
  reasonType: 'Report type',
  inputReason: 'Please enter a report reason.',
  reasonTooLong: 'Report reasons must be {max} characters or fewer.',
  reportSuccess: 'Report submitted.',
  reportFailed: 'Failed to submit report.',
  reasonTypes: {
    SPAM: 'Spam',
    ABUSE: 'Abuse or harassment',
    ADULT: 'Adult content',
    ETC: 'Other',
  },
  types: {
    post: 'Post',
    comment: 'Comment',
    user: 'User',
  },
}
