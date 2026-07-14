import type { NotificationMessages } from './types'

export const notificationEn: NotificationMessages = {
  title: 'Notifications',
  markAllRead: 'Mark all as read',
  markAllReadShort: 'Mark all read',
  empty: 'No new notifications.',
  emptyDescription: 'New comments, mentions, and messages will appear here.',
  groupedCount: '{count} notifications are grouped.',
  sourceTypes: {
    post: 'Post',
    comment: 'Comment',
    message: 'Message',
  },
  types: {
    default: 'Notification',
    like: 'Like',
    comment: 'Comment',
    reply: 'Reply',
    mention: 'Mention',
    message: 'Message',
    system: 'System',
    sanction: 'Sanction',
    keyword: 'Keyword',
    badge: 'Badge',
  },
}
