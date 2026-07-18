import type { BoardDetail } from '@/types'
import { isEmpty } from '@/utils/validation'

const BOARD_URL_DISALLOWED_INPUT_PATTERN = /[^a-z0-9_-]/g
const GENERAL_CATEGORY_NAMES = new Set(['일반', 'general'])

type BoardWriteContext = Pick<BoardDetail, 'categories' | 'isAdmin'>
type CategoryContext = {
  categoryId?: number | null
  name?: string | null
  sortOrder?: number | null
  isDefault?: boolean | null
}
type CategoryWriteContext = {
  minWriteRole?: string | null
}
type BoardRequiredFields = {
  boardName?: string | null
  boardUrl?: string | null
}
type BoardWriteFields = BoardRequiredFields & {
  description?: string | null
  iconUrl?: string | null
  guidePrompt?: string | null
  sortOrder?: number | null
}
type BoardRequiredFieldValidationResult =
  | { valid: true }
  | { valid: false, messageKey: 'board.form.validation', toastType: 'error' }

const BOARD_REQUIRED_FIELD_ERROR = {
  valid: false,
  messageKey: 'board.form.validation',
  toastType: 'error'
} as const

export const BOARD_WRITE_LIMITS = {
  boardName: 100,
  boardUrl: 100,
  description: 255,
  iconUrl: 255,
  guidePrompt: 5000,
} as const

const BOARD_URL_PATTERN = /^[a-z0-9_-]+$/

export function hasRequiredBoardFields(board: BoardRequiredFields): boolean {
  return validateRequiredBoardFields(board).valid
}

export function validateRequiredBoardFields(board: BoardRequiredFields): BoardRequiredFieldValidationResult {
  if (isEmpty(board.boardName) || isEmpty(board.boardUrl)) {
    return BOARD_REQUIRED_FIELD_ERROR
  }

  return { valid: true }
}

export function validateBoardWriteFields(board: BoardWriteFields): BoardRequiredFieldValidationResult {
  const requiredValidation = validateRequiredBoardFields(board)
  if (!requiredValidation.valid) return requiredValidation

  const boardName = String(board.boardName ?? '').trim()
  const boardUrl = String(board.boardUrl ?? '').trim()
  const description = String(board.description ?? '').trim()
  const iconUrl = String(board.iconUrl ?? '').trim()
  const guidePrompt = String(board.guidePrompt ?? '').trim()

  if (
    boardName.length > BOARD_WRITE_LIMITS.boardName
    || boardUrl.length > BOARD_WRITE_LIMITS.boardUrl
    || !BOARD_URL_PATTERN.test(boardUrl)
    || description.length > BOARD_WRITE_LIMITS.description
    || iconUrl.length > BOARD_WRITE_LIMITS.iconUrl
    || guidePrompt.length > BOARD_WRITE_LIMITS.guidePrompt
    || (board.sortOrder != null && board.sortOrder < 0)
  ) {
    return BOARD_REQUIRED_FIELD_ERROR
  }

  return { valid: true }
}

export function isGeneralCategoryName(name?: string | null): boolean {
  const normalized = name?.trim().toLowerCase()
  return normalized ? GENERAL_CATEGORY_NAMES.has(normalized) : false
}

export function normalizeBoardUrlInput(value?: string | number | null): string {
  return String(value ?? '').toLowerCase().replace(BOARD_URL_DISALLOWED_INPUT_PATTERN, '')
}

export function isDefaultCategory(category?: CategoryContext | null): boolean {
  return Boolean(category?.isDefault) || isGeneralCategoryName(category?.name)
}

export function resolveDefaultCategory<T extends CategoryContext>(categories?: T[] | null): T | undefined {
  if (!categories?.length) {
    return undefined
  }

  const orderedCategories = [...categories].sort((left, right) => {
    const sortCompare = (left.sortOrder ?? 0) - (right.sortOrder ?? 0)
    if (sortCompare !== 0) return sortCompare
    return (left.categoryId ?? 0) - (right.categoryId ?? 0)
  })

  return orderedCategories.find((category) => category.isDefault)
    ?? orderedCategories.find((category) => isGeneralCategoryName(category.name))
    ?? orderedCategories[0]
}

export function canWriteCategory(
  category: CategoryWriteContext | string | null | undefined,
  userRole?: string | null,
  isBoardAdmin = false
): boolean {
  const minRole = typeof category === 'string'
    ? category
    : category?.minWriteRole
  const normalizedRole = minRole || 'USER'
  const normalizedUserRole = userRole || 'USER'

  if (normalizedRole === 'SUPER_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN'
  }

  if (normalizedRole === 'BOARD_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN' || isBoardAdmin
  }

  return normalizedRole === 'USER'
}

export function canWriteBoardPost(
  board: BoardWriteContext | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null
): boolean {
  if (!isAuthenticated || !board) {
    return false
  }

  const categories = board.categories ?? []
  if (!categories.length) {
    return true
  }

  return categories.some((category) => canWriteCategory(category, userRole, board.isAdmin))
}
