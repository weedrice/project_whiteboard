import type { AdminMessages } from '../types'
import { adminCoreMessages } from './core'
import { adminUserMessages } from './users'
import { adminModerationMessages } from './moderation'
import { adminSystemMessages } from './system'

export const admin: AdminMessages = {
  ...adminCoreMessages,
  ...adminUserMessages,
  ...adminModerationMessages,
  ...adminSystemMessages,
}