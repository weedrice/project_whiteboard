import type { BoardDetail } from '@/types'

const GENERAL_CATEGORY_NAMES = new Set(['일반', 'general'])

type BoardWriteContext = Pick<BoardDetail, 'categories' | 'isAdmin'>
type CategoryContext = {
  categoryId?: number | null
  name?: string | null
  sortOrder?: number | null
  isDefault?: boolean | null
}

export function isGeneralCategoryName(name?: string | null): boolean {
  const normalized = name?.trim().toLowerCase()
  return normalized ? GENERAL_CATEGORY_NAMES.has(normalized) : false
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

export function canWriteBoardPost(
  board: BoardWriteContext | null | undefined,
  isAuthenticated: boolean,
  userRole?: string | null
): boolean {
  if (!isAuthenticated || !board) {
    return false
  }

  const defaultCategory = resolveDefaultCategory(board.categories)
  if (!defaultCategory) {
    return true
  }

  const minRole = defaultCategory.minWriteRole || 'USER'
  const normalizedUserRole = userRole || 'USER'

  if (minRole === 'SUPER_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN'
  }

  if (minRole === 'BOARD_ADMIN') {
    return normalizedUserRole === 'SUPER_ADMIN' || board.isAdmin
  }

  return true
}
