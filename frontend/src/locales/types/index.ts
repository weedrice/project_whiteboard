export * from './common'
export * from './community'
export * from './admin'

import type {
  CommonMessages,
  HomeMessages,
  LayoutMessages,
  SearchMessages,
} from './common'
import type {
  AuthMessages,
  BoardMessages,
  CommentMessages,
  EmoticonMessages,
  NotificationMessages,
  ReportMessages,
  UserMessages,
} from './community'
import type { AdminMessages } from './admin'

export interface OnboardingMessages {
  kicker: string
  title: string
  description: string
  recommendedBoards: string
  subscribe: string
  subscribed: string
  pushTitle: string
  pushDescription: string
  enablePush: string
  pushEnabled: string
  pushFailed: string
  skip: string
  finish: string
}

export interface Messages {
  common: CommonMessages
  search: SearchMessages
  home: HomeMessages
  layout: LayoutMessages
  auth: AuthMessages
  board: BoardMessages
  comment: CommentMessages
  notification: NotificationMessages
  user: UserMessages
  report: ReportMessages
  emoticon: EmoticonMessages
  admin: AdminMessages
  onboarding: OnboardingMessages
}

export type SupportedLocale = 'ko' | 'en'

export type LocaleMessages = Record<SupportedLocale, Messages>
