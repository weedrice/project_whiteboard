import type { LocaleMessages } from './types'
import { common, commonEn } from './common'
import { search } from './search'
import { home, homeEn } from './home'
import { layout, layoutEn } from './layout'
import { auth, authEn } from './auth'
import { board, boardEn } from './board'
import { comment, commentEn } from './comment'
import { notification, notificationEn } from './notification'
import { user } from './user'
import { report, reportEn } from './report'
import { emoticon, emoticonEn } from './emoticon'
import { admin } from './admin'
import { onboarding, onboardingEn } from './onboarding'

export const messages = {
  ko: {
    common,
    search,
    home,
    layout,
    auth,
    board,
    comment,
    notification,
    user,
    report,
    emoticon,
    admin,
    onboarding,
  },
  en: {
    common: commonEn,
    search,
    home: homeEn,
    layout: layoutEn,
    auth: authEn,
    board: boardEn,
    comment: commentEn,
    notification: notificationEn,
    user,
    report: reportEn,
    emoticon: emoticonEn,
    admin,
    onboarding: onboardingEn,
  },
} satisfies LocaleMessages

export type AppLocaleMessages = typeof messages

export default messages
