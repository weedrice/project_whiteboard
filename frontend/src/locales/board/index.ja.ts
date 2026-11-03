import type { BoardMessages } from '../types'
import { boardJaBaseMessages } from './base.ja'
import { boardJaWritePostMessages } from './writePost.ja'

export const boardJa: BoardMessages = {
  ...boardJaBaseMessages,
  writePost: boardJaWritePostMessages,
}
