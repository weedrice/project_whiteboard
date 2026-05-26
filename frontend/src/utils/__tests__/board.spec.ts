import { describe, expect, it } from 'vitest'
import {
  canWriteBoardPost,
  canWriteCategory,
  hasRequiredBoardFields,
  isDefaultCategory,
  isGeneralCategoryName,
  normalizeBoardUrlInput,
  resolveDefaultCategory,
  validateRequiredBoardFields
} from '../board'

describe('board utils', () => {
  it('matches general category names across locales', () => {
    expect(isGeneralCategoryName('\uC77C\uBC18')).toBe(true)
    expect(isGeneralCategoryName('general')).toBe(true)
    expect(isGeneralCategoryName('qna')).toBe(false)
  })

  it('normalizes board URL input to lowercase letters and underscores only', () => {
    expect(normalizeBoardUrlInput('Free_BOARD_123-\uD55C\uAE00')).toBe('free_board_')
    expect(normalizeBoardUrlInput(null)).toBe('')
  })

  it('checks required board form fields with shared trimmed validation semantics', () => {
    expect(hasRequiredBoardFields({ boardName: 'Board', boardUrl: 'board' })).toBe(true)
    expect(hasRequiredBoardFields({ boardName: '', boardUrl: 'board' })).toBe(false)
    expect(hasRequiredBoardFields({ boardName: 'Board', boardUrl: '' })).toBe(false)
    expect(hasRequiredBoardFields({ boardName: ' ', boardUrl: 'board' })).toBe(false)
    expect(validateRequiredBoardFields({ boardName: ' ', boardUrl: 'board' })).toEqual({
      valid: false,
      messageKey: 'board.form.validation',
      toastType: 'error'
    })
  })

  it('uses explicit default category flags before legacy name fallback', () => {
    const categories = [
      { categoryId: 1, name: '\uC77C\uBC18', sortOrder: 1, isActive: true, minWriteRole: 'USER' },
      { categoryId: 2, name: 'Restricted', sortOrder: 2, isActive: true, minWriteRole: 'BOARD_ADMIN', isDefault: true }
    ]

    expect(isDefaultCategory(categories[1])).toBe(true)
    expect(resolveDefaultCategory(categories)?.categoryId).toBe(2)
  })

  it('allows writing when there is no restricted general category', () => {
    expect(canWriteBoardPost({
      isAdmin: false,
      categories: [
        { categoryId: 2, name: 'QnA', sortOrder: 1, isActive: true, minWriteRole: 'USER' }
      ]
    }, true, 'USER')).toBe(true)
  })

  it('blocks writing for unauthenticated users', () => {
    expect(canWriteBoardPost({
      isAdmin: false,
      categories: [
        { categoryId: 1, name: '\uC77C\uBC18', sortOrder: 1, isActive: true, minWriteRole: 'USER' }
      ]
    }, false, 'USER')).toBe(false)
  })

  it('requires board admin or super admin when the general category is restricted', () => {
    const board = {
      isAdmin: false,
      categories: [
        { categoryId: 1, name: '\uC77C\uBC18', sortOrder: 1, isActive: true, minWriteRole: 'BOARD_ADMIN' }
      ]
    }

    expect(canWriteBoardPost(board, true, 'USER')).toBe(false)
    expect(canWriteBoardPost({ ...board, isAdmin: true }, true, 'USER')).toBe(true)
    expect(canWriteBoardPost(board, true, 'SUPER_ADMIN')).toBe(true)
  })

  it('uses the explicit default category write role even when the name is custom', () => {
    const board = {
      isAdmin: false,
      categories: [
        { categoryId: 1, name: 'General', sortOrder: 1, isActive: true, minWriteRole: 'USER' },
        { categoryId: 2, name: 'Restricted', sortOrder: 2, isActive: true, minWriteRole: 'BOARD_ADMIN', isDefault: true }
      ]
    }

    expect(canWriteBoardPost(board, true, 'USER')).toBe(false)
    expect(canWriteBoardPost({ ...board, isAdmin: true }, true, 'USER')).toBe(true)
  })

  it('checks category-level write roles consistently', () => {
    expect(canWriteCategory({ minWriteRole: 'USER' }, 'USER', false)).toBe(true)
    expect(canWriteCategory({ minWriteRole: 'BOARD_ADMIN' }, 'USER', false)).toBe(false)
    expect(canWriteCategory({ minWriteRole: 'BOARD_ADMIN' }, 'USER', true)).toBe(true)
    expect(canWriteCategory({ minWriteRole: 'SUPER_ADMIN' }, 'BOARD_ADMIN', true)).toBe(false)
    expect(canWriteCategory({ minWriteRole: 'SUPER_ADMIN' }, 'SUPER_ADMIN', false)).toBe(true)
    expect(canWriteCategory({ minWriteRole: 'UNKNOWN' }, 'SUPER_ADMIN', true)).toBe(false)
  })
})
