import type { AdminMessages } from '../types'
import { adminCoreMessagesJa } from './core.ja'
import { adminUserMessagesJa } from './users.ja'
import { adminModerationMessagesJa } from './moderation.ja'
import { adminSystemMessagesJa } from './system.ja'
import { adminShopMessagesJa } from './shop.ja'

export const adminJa: AdminMessages = {
  ...adminCoreMessagesJa,
  ...adminUserMessagesJa,
  ...adminModerationMessagesJa,
  ...adminSystemMessagesJa,
  ...adminShopMessagesJa,
}
