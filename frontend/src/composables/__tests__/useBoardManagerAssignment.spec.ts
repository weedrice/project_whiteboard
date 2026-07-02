import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardManagerAssignment } from '../useBoardManagerAssignment'
import { createDeferred } from '@/test/async'
import type { AdminBoard } from '@/types'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

function board(overrides: Partial<AdminBoard>): AdminBoard {
  return {
    boardId: 1,
    boardName: 'Board',
    boardUrl: 'board',
    sortOrder: 0,
    adminDisplayName: 'Old Manager',
    allowNsfw: false,
    isActive: true,
    isPublic: true,
    agentUseYn: false,
    ...overrides,
  }
}

describe('useBoardManagerAssignment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('updates the board captured when manager assignment starts', async () => {
    const boardA = board({ boardId: 1, boardName: 'A', adminDisplayName: 'Manager A' })
    const boardB = board({ boardId: 2, boardName: 'B', adminDisplayName: 'Manager B' })
    const selectedBoard = ref<AdminBoard | null>(boardA)
    const updateResult = createDeferred<unknown>()
    const updateBoardManager = vi.fn(() => updateResult.promise)
    const assignment = useBoardManagerAssignment({
      selectedBoard: computed(() => selectedBoard.value),
      boardManagerData: ref(null),
      updateBoardManager,
    })

    const pending = assignment.confirmManagerSelection([
      { loginId: 'new-manager', displayName: 'New Manager' },
    ])
    selectedBoard.value = boardB
    updateResult.resolve(undefined)
    await pending

    expect(updateBoardManager).toHaveBeenCalledWith({
      boardId: 1,
      data: { loginId: 'new-manager' },
    })
    expect(boardA.adminDisplayName).toBe('New Manager')
    expect(boardB.adminDisplayName).toBe('Manager B')
    expect(mocks.addToast).toHaveBeenCalledWith('admin.admins.messages.added', 'success')
  })

  it('skips assignment without a selected board or selected user', async () => {
    const updateBoardManager = vi.fn()
    const assignment = useBoardManagerAssignment({
      selectedBoard: computed(() => null),
      boardManagerData: ref(null),
      updateBoardManager,
    })

    await assignment.confirmManagerSelection([{ loginId: 'new-manager' }])
    await assignment.confirmManagerSelection([])

    expect(updateBoardManager).not.toHaveBeenCalled()
  })
})
