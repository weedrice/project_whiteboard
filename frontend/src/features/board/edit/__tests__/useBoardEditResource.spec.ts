import { describe, expect, it } from 'vitest'
import {
  assertBoardManageable,
  createEmptyBoardEditForm,
  resolveBoardManagerLabel,
  toBoardEditForm
} from '../useBoardEditResource'
import type { BoardDetail } from '@/types'

function createBoardDetail(overrides: Partial<BoardDetail> = {}): BoardDetail {
  return {
    boardId: 1,
    boardName: 'Free Board',
    boardUrl: 'free',
    description: 'Description',
    iconUrl: 'icon.png',
    sortOrder: 0,
    subscriberCount: 10,
    postCount: 20,
    adminDisplayName: 'Manager',
    isSubscribed: false,
    isActive: true,
    isPublic: true,
    isListed: true,
    subscriptionAccessible: true,
    allowNsfw: false,
    isAdmin: true,
    categories: [],
    latestPosts: [],
    adminUserId: 1,
    agentUseYn: false,
    guidePrompt: 'Guide',
    ...overrides
  }
}

describe('useBoardEditResource', () => {
  it('creates an empty board edit form with existing BoardForm defaults', () => {
    expect(createEmptyBoardEditForm()).toEqual({
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
    })
  })

  it('maps board detail response fields into BoardForm initial data', () => {
    expect(toBoardEditForm(createBoardDetail({
      boardName: 'Q&A',
      boardUrl: 'qna',
      description: undefined,
      iconUrl: undefined,
      sortOrder: 0,
      allowNsfw: true,
      isPublic: false,
      isListed: false,
      agentUseYn: true,
      guidePrompt: undefined
    }))).toEqual({
      boardName: 'Q&A',
      boardUrl: 'qna',
      description: '',
      iconUrl: '',
      sortOrder: 0,
      allowNsfw: true,
      isPublic: false,
      isListed: false,
      agentUseYn: true,
      guidePrompt: ''
    })
  })

  it('resolves manager label with a fallback for empty backend values', () => {
    expect(resolveBoardManagerLabel(createBoardDetail({ adminDisplayName: 'Manager' }), 'common.noData')).toBe('Manager')
    expect(resolveBoardManagerLabel(createBoardDetail({ adminDisplayName: undefined }), 'common.noData')).toBe('common.noData')
  })

  it('checks whether the loaded board is manageable before rendering the edit form', () => {
    expect(assertBoardManageable(createBoardDetail({ isAdmin: true }))).toBe(true)
    expect(assertBoardManageable(createBoardDetail({ isAdmin: false }))).toBe(false)
  })
})
