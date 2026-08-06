import type { BoardDetail } from '@/types'

export interface BoardEditFormData {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: number
  allowNsfw: boolean
  isPublic: boolean
  isListed?: boolean
  agentUseYn: boolean
  guidePrompt: string
}

export function createEmptyBoardEditForm(): BoardEditFormData {
  return {
    boardName: '',
    boardUrl: '',
    description: '',
    iconUrl: '',
    sortOrder: 0,
    allowNsfw: false,
    isPublic: true,
    isListed: true,
    agentUseYn: false,
    guidePrompt: ''
  }
}

export function toBoardEditForm(board: BoardDetail): BoardEditFormData {
  return {
    boardName: board.boardName,
    boardUrl: board.boardUrl,
    description: board.description || '',
    iconUrl: board.iconUrl || '',
    sortOrder: board.sortOrder ?? 0,
    allowNsfw: board.allowNsfw || false,
    isPublic: board.isPublic ?? true,
    isListed: board.isListed ?? (board.isPublic ?? true),
    agentUseYn: board.agentUseYn ?? false,
    guidePrompt: board.guidePrompt || ''
  }
}

export function resolveBoardManagerLabel(
  board: Pick<BoardDetail, 'adminDisplayName'>,
  fallbackLabel: string
): string {
  return board.adminDisplayName || fallbackLabel
}

export function assertBoardManageable(board: Pick<BoardDetail, 'isAdmin'>): boolean {
  return Boolean(board.isAdmin)
}
