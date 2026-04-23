import type { BoardDetail } from '@/types'

const GENERAL_CATEGORY_NAMES = new Set(['일반', 'general'])

type BoardWriteContext = Pick<BoardDetail, 'categories' | 'isAdmin'>

export function isGeneralCategoryName(name?: string | null): boolean {
  const normalized = name?.trim().toLowerCase()
  return normalized ? GENERAL_CATEGORY_NAMES.has(normalized) : false
}

export function canWriteBoardPost(
  board: BoardWriteContext | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null
): boolean {
  if (!isAuthenticated || !board) {
    return false
  }

  const generalCategory = board.categories?.find((category) => isGeneralCategoryName(category.name))
  if (!generalCategory) {
    return true
  }

  const minRole = generalCategory.minWriteRole || 'USER'
  const normalizedUserRole = userRole || 'USER'

  if (minRole === 'SUPER_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN'
  }

  if (minRole === 'BOARD_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN' || board.isAdmin
  }

  return true
}
