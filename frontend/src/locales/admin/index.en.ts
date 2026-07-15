import type { AdminMessages } from '../types'
import { adminCoreMessagesEn } from './core.en'
import { adminUserMessagesEn } from './users.en'
import { adminModerationMessagesEn } from './moderation.en'
import { adminSystemMessagesEn } from './system.en'

export const adminEn: AdminMessages = {
  ...adminCoreMessagesEn,
  ...adminUserMessagesEn,
  ...adminModerationMessagesEn,
  ...adminSystemMessagesEn,
}
