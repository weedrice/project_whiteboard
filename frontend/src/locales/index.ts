import type { LocaleMessages } from './types'
import { common, commonEn } from './common'
import { search } from './search'
import { home, homeEn } from './home'
import { layout, layoutEn } from './layout'
import { auth } from './auth'
import { board, boardEn } from './board'
import { comment } from './comment'
import { notification } from './notification'
import { user } from './user'
import { report } from './report'
import { emoticon } from './emoticon'
import { admin } from './admin'

export const messages: LocaleMessages = {
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
  },
  en: {
    common: commonEn,
    search,
    home: homeEn,
    layout: layoutEn,
    auth,
    board: boardEn,
    comment,
    notification,
    user,
    report,
    emoticon,
    admin,
  },
}

export default messages
