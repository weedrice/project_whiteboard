import type { BoardMessages } from '../types'
import { boardBaseMessages, boardEnBaseMessages } from './base'
import { boardWritePostMessages, boardEnWritePostMessages } from './writePost'

export const board: BoardMessages = {
  ...boardBaseMessages,
  writePost: boardWritePostMessages,
}

export const boardEn: BoardMessages = {
  ...board,
  ...boardEnBaseMessages,
  writePost: boardEnWritePostMessages,
}