import type { AdminMessages } from '../types'
import { adminCoreMessages, adminCoreMessagesEn } from './core'
import { adminUserMessages, adminUserMessagesEn } from './users'
import { adminModerationMessages, adminModerationMessagesEn } from './moderation'
import { adminSystemMessages, adminSystemMessagesEn } from './system'

export const admin: AdminMessages = {
  ...adminCoreMessages,
  ...adminUserMessages,
  ...adminModerationMessages,
  ...adminSystemMessages,
}

export const adminEn: AdminMessages = {
  ...adminCoreMessagesEn,
  ...adminUserMessagesEn,
  ...adminModerationMessagesEn,
  ...adminSystemMessagesEn,
}
