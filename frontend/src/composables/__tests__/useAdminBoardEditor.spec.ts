import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminBoardEditor } from '../useAdminBoardEditor'
import type { AdminBoard } from '@/types'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))

const confirmMock = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/stores/toast', () => ({
  useToastStore: () => toastMock
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: confirmMock
  })
}))

function createBoard(overrides: Partial<AdminBoard>): AdminBoard {
  return {
    boardId: 1,
    boardName: 'Board',
    boardUrl: 'board',
    description: '',
    iconUrl: '',
    sortOrder: 1,
    adminDisplayName: '',
    adminUserId: undefined,
    allowNsfw: false,
    isActive: true,
    isPublic: true,
    agentUseYn: false,
    guidePrompt: '',
    ...overrides
  }
}

describe('useAdminBoardEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmMock.mockResolvedValue(true)
  })

  it('sorts admin boards and selects the first board for editing', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'Second', boardUrl: 'second', sortOrder: 2 }),
      createBoard({ boardId: 2, boardName: 'First', boardUrl: 'first', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = useAdminBoardEditor({ boardsData, updateBoard })
    await nextTick()

    expect(editor.boards.value.map((board) => board.boardId)).toEqual([2, 1])
    expect(editor.selectedBoardId.value).toBe(2)
    expect(editor.form.boardName).toBe('First')
  })

  it('saves form edits with the original boardUrl request key', async () => {
    const boardsData = ref([
      createBoard({ boardId: 10, boardName: 'Old', boardUrl: 'old-url', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = useAdminBoardEditor({ boardsData, updateBoard })
    await nextTick()

    editor.form.boardName = 'New'
    editor.form.boardUrl = 'new-url'
    await editor.handleSaveChanges()

    expect(updateBoard).toHaveBeenCalledWith({
      boardUrl: 'old-url',
      data: expect.objectContaining({
        boardName: 'New',
        boardUrl: 'new-url',
        sortOrder: 1
      })
    })
    expect(toastMock.addToast).toHaveBeenCalledWith('common.messages.saveSuccess', 'success')
  })

  it('keeps the selected board when unsaved changes are not confirmed', async () => {
    confirmMock.mockResolvedValue(false)
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'First', boardUrl: 'first', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 2 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = useAdminBoardEditor({ boardsData, updateBoard })
    await nextTick()

    editor.form.boardName = 'Unsaved'
    await editor.selectBoard(boardsData.value[1])

    expect(confirmMock).toHaveBeenCalledWith('admin.boards.messages.confirmDiscardChanges')
    expect(editor.selectedBoardId.value).toBe(1)
  })

  it('blocks saving and shows the shared validation toast when required fields are missing', async () => {
    const boardsData = ref([
      createBoard({ boardId: 10, boardName: 'Old', boardUrl: 'old-url', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = useAdminBoardEditor({ boardsData, updateBoard })
    await nextTick()

    editor.form.boardName = ''
    await editor.handleSaveChanges()

    expect(updateBoard).not.toHaveBeenCalled()
    expect(toastMock.addToast).toHaveBeenCalledWith('board.writePost.validation', 'warning')
  })
})
