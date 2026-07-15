import type { BoardMessages } from '../types'
import { boardEnBaseMessages } from './base.en'
import { boardEnWritePostMessages } from './writePost.en'
import { board } from './index'

export const boardEn: BoardMessages = {
  ...board,
  ...boardEnBaseMessages,
  writePost: boardEnWritePostMessages,
}
