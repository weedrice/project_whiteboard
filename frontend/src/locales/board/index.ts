import type { BoardMessages } from '../types'
import { boardBaseMessages } from './base'
import { boardWritePostMessages } from './writePost'

export const board: BoardMessages = {
  ...boardBaseMessages,
  writePost: boardWritePostMessages,
}
