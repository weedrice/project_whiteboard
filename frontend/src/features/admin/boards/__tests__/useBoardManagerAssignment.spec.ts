import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
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
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('does not apply a stale manager response after the selected board changes', async () => {
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
    assignment.openManagerModal()

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
    expect(boardA.adminDisplayName).toBe('Manager A')
    expect(boardB.adminDisplayName).toBe('Manager B')
    expect(assignment.isManagerModalOpen.value).toBe(true)
    expect(mocks.addToast).not.toHaveBeenCalled()
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

  it('updates and closes only the current board modal', async () => {
    const selected = board({ boardId: 3, adminDisplayName: 'Old' })
    const updateBoardManager = vi.fn().mockResolvedValue(undefined)
    const assignment = useBoardManagerAssignment({
      selectedBoard: computed(() => selected),
      boardManagerData: ref(null),
      updateBoardManager,
    })
    assignment.openManagerModal()

    await assignment.confirmManagerSelection([{ loginId: 'manager', displayName: 'New' }])

    expect(selected.adminDisplayName).toBe('New')
    expect(assignment.isManagerModalOpen.value).toBe(false)
    expect(mocks.addToast).toHaveBeenCalledWith('admin.admins.messages.added', 'success')
  })
})
