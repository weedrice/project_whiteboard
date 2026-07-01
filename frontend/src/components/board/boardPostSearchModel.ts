export type BoardPostSearchType = 'TITLE_CONTENT' | 'TITLE' | 'CONTENT' | 'AUTHOR'

export interface BoardPostSearchTypeOption {
  value: BoardPostSearchType
  label: string
}

type Translate = (key: string) => string

export const BOARD_POST_SEARCH_TYPES: BoardPostSearchType[] = [
  'TITLE_CONTENT',
  'TITLE',
  'CONTENT',
  'AUTHOR',
]

const SEARCH_TYPE_LABEL_KEYS: Record<BoardPostSearchType, string> = {
  TITLE_CONTENT: 'board.detail.searchType.titleContent',
  TITLE: 'board.detail.searchType.title',
  CONTENT: 'board.detail.searchType.content',
  AUTHOR: 'board.detail.searchType.author',
}

export function createBoardPostSearchTypeOptions(t: Translate): BoardPostSearchTypeOption[] {
  return BOARD_POST_SEARCH_TYPES.map((value) => ({
    value,
    label: t(SEARCH_TYPE_LABEL_KEYS[value]),
  }))
}

export function shouldShowBoardPostSearchClear(isSearching: boolean, searchQuery: string): boolean {
  return isSearching || searchQuery.length > 0
}
