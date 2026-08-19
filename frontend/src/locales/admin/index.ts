import type { AdminMessages } from '../types'
import { adminCoreMessages } from './core'
import { adminUserMessages } from './users'
import { adminModerationMessages } from './moderation'
import { adminSystemMessages } from './system'
import { adminShopMessages } from './shop'

export const admin: AdminMessages = {
  ...adminCoreMessages,
  ...adminUserMessages,
  ...adminModerationMessages,
  ...adminSystemMessages,
  ...adminShopMessages,
}
