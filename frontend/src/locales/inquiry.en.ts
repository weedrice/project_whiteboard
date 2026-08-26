import type { InquiryMessages } from './types'

export const inquiryEn: InquiryMessages = {
  common: {
    all: 'All', loading: 'Loading…', loadFailed: 'Failed to load.', notFound: 'This inquiry was not found or is unavailable.', empty: 'No inquiries yet.',
    status: 'Status', category: 'Category', priority: 'Priority', fromDate: 'From', toDate: 'To', search: 'Search', searchPlaceholder: 'Search title', query: 'Search', close: 'Close', remove: 'Remove', image: 'Image',
  },
  category: { ACCOUNT: 'Account', SERVICE_USE: 'Service use', TECHNICAL: 'Technical', CONTENT_OPERATION: 'Content operations', SUGGESTION: 'Suggestion', OTHER: 'Other' },
  status: { NEW: 'New', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed' },
  priority: { NORMAL: 'Normal', HIGH: 'High', URGENT: 'Urgent' },
  closureReason: { WITHDRAWN: 'Withdrawn by user', USER_CONFIRMED: 'Confirmed resolved by user', ADMIN_CLOSED: 'Closed by administrator', AUTO_CLOSED: 'Closed automatically' },
  list: { title: 'My inquiries', description: 'Review inquiries kept separate from community posts.', create: 'New inquiry', modifiedAt: 'Updated {date}' },
  form: { title: 'New inquiry', description: 'Your plain-text message is visible only to support staff.', category: 'Category', subject: 'Title', content: 'Message', count: '{count}/10,000', cancel: 'Cancel', submit: 'Submit', validation: 'Use 1–200 characters for the title and 1–10,000 for the message.', failed: 'Failed to submit the inquiry.', createdNavigationFailed: 'The inquiry was created, but the detail page could not be opened.', openCreated: 'Open created inquiry' },
  detail: { title: 'Inquiry details', description: 'Review status and the public conversation.', list: 'Back to list', addMessage: 'Follow-up', submitMessage: 'Add reply', messageValidation: 'Use 1–10,000 characters.', messageFailed: 'Failed to add the message.', actionFailed: 'The request could not be completed.', withdraw: 'Withdraw', close: 'Close inquiry', withdrawConfirm: 'Withdraw this unanswered inquiry?', closeConfirm: 'Close this resolved inquiry?' },
  timeline: { label: 'Inquiry conversation', USER_MESSAGE: 'User message', STAFF_REPLY: 'Staff reply', INTERNAL_NOTE: 'Internal note' },
  upload: { choose: 'Attach images ({count}/5)', uploading: 'Uploading…', remove: 'Remove', fallbackName: 'File #{id}', max: 'Attach up to five images per message.', invalid: 'Only JPEG, PNG, GIF, and WebP images up to 10 MiB are supported.', failed: 'Image upload failed.' },
  admin: { title: 'Inquiry management', description: 'Manage new inquiries and the legacy archive separately.', newTab: 'New inquiries', legacyTab: 'Legacy inquiries', loadFailed: 'Failed to load new inquiries.', empty: 'No inquiries match these filters.', author: 'Author', waitingSince: 'Waiting since', archiveNotice: 'Legacy board inquiries are available read-only during stabilization.', legacyEmpty: 'No legacy inquiries.', total: '{count} total', detail: 'New inquiry details', start: 'Start handling', reopen: 'Reopen', close: 'Admin close', publicReply: 'Public reply', note: 'Internal note', notePlaceholder: 'Hidden from the user', replyPlaceholder: 'Reply visible to the user', addNote: 'Add note', addReply: 'Send reply', closureReason: 'Closure reason: {reason}', closePrompt: 'Enter an administrator closure reason.', closeReasonRequired: 'A closure reason is required.', actionFailed: 'The inquiry action failed.', contentValidation: 'Use 1–10,000 characters.', legacyTitle: 'Title', legacyContent: 'Content', createdAt: 'Created' },
}
