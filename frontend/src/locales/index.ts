import type { LocaleMessages } from './types'
import { common } from './common'
import { search } from './search'
import { home } from './home'
import { layout } from './layout'
import { auth } from './auth'
import { board } from './board'
import { comment } from './comment'
import { notification } from './notification'
import { user } from './user'
import { report } from './report'
import { emoticon } from './emoticon'
import { admin } from './admin'
import { onboarding } from './onboarding'
import { privacyPolicy } from './privacy'
import { shop } from './shop'
import { inquiry } from './inquiry'

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
    privacy: privacyPolicy,
    shop,
    inquiry,
  },
} satisfies Pick<LocaleMessages, 'ko'>

export type AppLocaleMessages = typeof messages

export default messages
