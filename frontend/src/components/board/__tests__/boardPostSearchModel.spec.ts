import { describe, expect, it } from 'vitest'
import {
  BOARD_POST_SEARCH_TYPES,
  createBoardPostSearchTypeOptions,
  shouldShowBoardPostSearchClear,
} from '../boardPostSearchModel'

describe('boardPostSearchModel', () => {
  it('creates stable search type options in display order', () => {
    const options = createBoardPostSearchTypeOptions((key) => `t:${key}`)

    expect(options.map((option) => option.value)).toEqual(BOARD_POST_SEARCH_TYPES)
    expect(options.map((option) => option.label)).toEqual([
      't:board.detail.searchType.titleContent',
      't:board.detail.searchType.title',
      't:board.detail.searchType.content',
      't:board.detail.searchType.author',
    ])
  })

  it('shows clear action while searching or when query has content', () => {
    expect(shouldShowBoardPostSearchClear(false, '')).toBe(false)
    expect(shouldShowBoardPostSearchClear(true, '')).toBe(true)
    expect(shouldShowBoardPostSearchClear(false, 'keyword')).toBe(true)
  })
})
