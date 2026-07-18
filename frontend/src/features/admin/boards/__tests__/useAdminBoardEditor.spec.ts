import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminBoardEditor } from '../useAdminBoardEditor'
import { createDeferred } from '@/test/async'
import type { AdminBoard } from '@/types'

const toastMock = vi.hoisted(() => ({
  addToast: vi.fn()
}))

const confirmMock = vi.hoisted(() => vi.fn())
const confirmWithReasonMock = vi.hoisted(() => vi.fn())

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
    confirm: confirmMock,
    confirmWithReason: confirmWithReasonMock,
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

function createEditor(
  boardsData: ReturnType<typeof ref<AdminBoard[] | undefined>>,
  updateBoard: ReturnType<typeof vi.fn>,
  reorderBoards: ReturnType<typeof vi.fn> = vi.fn(async (boardIds: number[]) => boardIds.map((boardId, index) => ({
    ...(boardsData.value ?? []).find((board) => board.boardId === boardId)!,
    sortOrder: index + 1,
  }))),
) {
  type EditorOptions = Parameters<typeof useAdminBoardEditor>[0]
  return useAdminBoardEditor({
    boardsData,
    updateBoard: updateBoard as EditorOptions['updateBoard'],
    reorderBoards: reorderBoards as EditorOptions['reorderBoards'],
    refetchBoards: vi.fn().mockResolvedValue(undefined),
  })
}

describe('useAdminBoardEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirmMock.mockResolvedValue(true)
    confirmWithReasonMock.mockResolvedValue('maintenance')
  })

  it('sorts admin boards and selects the first board for editing', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'Second', boardUrl: 'second', sortOrder: 2 }),
      createBoard({ boardId: 2, boardName: 'First', boardUrl: 'first', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = createEditor(boardsData, updateBoard)
    await nextTick()

    expect(editor.boards.value.map((board) => board.boardId)).toEqual([2, 1])
    expect(editor.selectedBoardId.value).toBe(2)
    expect(editor.form.boardName).toBe('First')
  })

  it('saves form edits with the original boardUrl request key', async () => {
    const boardsData = ref([
      createBoard({ boardId: 10, boardName: 'Old', boardUrl: 'old_url', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = createEditor(boardsData, updateBoard)
    await nextTick()

    editor.form.boardName = '  New  '
    editor.form.boardUrl = 'New-URL_한글'
    await nextTick()
    await editor.handleSaveChanges()

    expect(updateBoard).toHaveBeenCalledWith({
      boardUrl: 'old_url',
      data: expect.objectContaining({
        boardName: 'New',
        boardUrl: 'new-url_'
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

    const editor = createEditor(boardsData, updateBoard)
    await nextTick()

    editor.form.boardName = 'Unsaved'
    await editor.selectBoard(boardsData.value[1])

    expect(confirmMock).toHaveBeenCalledWith('admin.boards.messages.confirmDiscardChanges')
    expect(editor.selectedBoardId.value).toBe(1)
  })

  it('preserves the selected draft when refreshed board data arrives', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'Original', boardUrl: 'original', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 2 }),
    ])
    const editor = createEditor(boardsData, vi.fn().mockResolvedValue(undefined))
    await nextTick()
    editor.form.boardName = 'Unsaved draft'

    boardsData.value = [
      createBoard({ boardId: 1, boardName: 'Refreshed', boardUrl: 'original', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second refreshed', boardUrl: 'second', sortOrder: 2 }),
    ]
    await nextTick()

    expect(editor.form.boardName).toBe('Unsaved draft')
    expect(editor.selectedBoard.value?.boardName).toBe('Refreshed')
    expect(editor.hasUnsavedChanges.value).toBe(true)
  })

  it('preserves a locally reordered list when refreshed board data arrives', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'First', boardUrl: 'first', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 2 }),
    ])
    const reorderResult = createDeferred<AdminBoard[]>()
    const editor = createEditor(
      boardsData,
      vi.fn().mockResolvedValue(undefined),
      vi.fn().mockReturnValue(reorderResult.promise),
    )
    await nextTick()
    editor.boards.value = [editor.boards.value[1], editor.boards.value[0]]
    const pendingSave = editor.handleDragEnd()

    boardsData.value = [
      createBoard({ boardId: 1, boardName: 'First refreshed', boardUrl: 'first', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second refreshed', boardUrl: 'second', sortOrder: 2 }),
      createBoard({ boardId: 3, boardName: 'New', boardUrl: 'new', sortOrder: 3 }),
    ]
    await nextTick()

    expect(editor.boards.value.map((board) => board.boardId)).toEqual([2, 1, 3])
    expect(editor.boards.value.map((board) => board.sortOrder)).toEqual([1, 2, 3])

    reorderResult.resolve([
      createBoard({ boardId: 2, boardName: 'Second refreshed', boardUrl: 'second', sortOrder: 1 }),
      createBoard({ boardId: 1, boardName: 'First refreshed', boardUrl: 'first', sortOrder: 2 }),
      createBoard({ boardId: 3, boardName: 'New', boardUrl: 'new', sortOrder: 3 }),
    ])
    await pendingSave
  })

  it('does not save when board deactivation is cancelled', async () => {
    const boardsData = ref([createBoard({ boardId: 10, boardUrl: 'old-url' })])
    const updateBoard = vi.fn().mockResolvedValue(undefined)
    const editor = createEditor(boardsData, updateBoard)
    await nextTick()
    editor.form.isActive = false
    confirmWithReasonMock.mockResolvedValueOnce(null)

    await editor.handleSaveChanges()

    expect(confirmWithReasonMock).toHaveBeenCalledWith(
      'admin.boards.messages.confirmDeactivate',
      undefined,
      undefined,
      undefined,
      undefined,
      { maxLength: 500 },
    )
    expect(updateBoard).not.toHaveBeenCalled()
  })

  it('blocks saving and shows the shared validation toast when required fields are missing', async () => {
    const boardsData = ref([
      createBoard({ boardId: 10, boardName: 'Old', boardUrl: 'old-url', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)

    const editor = createEditor(boardsData, updateBoard)
    await nextTick()

    editor.form.boardName = ''
    await editor.handleSaveChanges()

    expect(updateBoard).not.toHaveBeenCalled()
    expect(toastMock.addToast).toHaveBeenCalledWith('board.writePost.validation', 'warning')
  })

  it('blocks saving when a board field exceeds the backend contract', async () => {
    const boardsData = ref([createBoard({ boardId: 10, boardUrl: 'old-url' })])
    const updateBoard = vi.fn()
    const editor = createEditor(boardsData, updateBoard)
    await nextTick()
    editor.form.description = 'a'.repeat(256)

    await editor.handleSaveChanges()

    expect(updateBoard).not.toHaveBeenCalled()
    expect(toastMock.addToast).toHaveBeenCalledWith('board.writePost.validation', 'warning')
  })

  it('rolls back local form edits when saving fails', async () => {
    const boardsData = ref([
      createBoard({ boardId: 10, boardName: 'Old', boardUrl: 'old-url', sortOrder: 1 })
    ])
    const updateBoard = vi.fn().mockRejectedValue(new Error('failed'))

    const editor = createEditor(boardsData, updateBoard)
    await nextTick()

    editor.form.boardName = 'New'
    await editor.handleSaveChanges()

    expect(updateBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardUrl: 'old-url'
    }))
    expect(editor.boards.value[0].boardName).toBe('Old')
    expect(editor.form.boardName).toBe('Old')
    expect(editor.hasUnsavedChanges.value).toBe(false)
  })

  it('rolls back dragged board order when sort saving fails', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'First', boardUrl: 'first', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 2 })
    ])
    const updateBoard = vi.fn().mockResolvedValue(undefined)
    const reorderBoards = vi.fn().mockRejectedValue(new Error('failed'))

    const editor = createEditor(boardsData, updateBoard, reorderBoards)
    await nextTick()

    editor.boards.value = [editor.boards.value[1], editor.boards.value[0]]
    await editor.handleDragEnd()

    expect(updateBoard).not.toHaveBeenCalled()
    expect(reorderBoards).toHaveBeenCalledWith([2, 1])
    expect(editor.boards.value.map((board) => board.boardId)).toEqual([1, 2])
    expect(editor.boards.value.map((board) => board.sortOrder)).toEqual([1, 2])
    expect(editor.hasUnsavedChanges.value).toBe(false)
  })

  it('ignores additional drag saves while a sort save is pending', async () => {
    const boardsData = ref([
      createBoard({ boardId: 1, boardName: 'First', boardUrl: 'first', sortOrder: 1 }),
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 2 })
    ])
    const saveResult = createDeferred<AdminBoard[]>()
    const updateBoard = vi.fn().mockResolvedValue(undefined)
    const reorderBoards = vi.fn().mockReturnValue(saveResult.promise)

    const editor = createEditor(boardsData, updateBoard, reorderBoards)
    await nextTick()

    editor.boards.value = [editor.boards.value[1], editor.boards.value[0]]
    const firstDragSave = editor.handleDragEnd()

    expect(editor.isSavingSortOrder.value).toBe(true)
    expect(reorderBoards).toHaveBeenCalledTimes(1)

    await editor.handleDragEnd()

    expect(reorderBoards).toHaveBeenCalledTimes(1)

    saveResult.resolve([
      createBoard({ boardId: 2, boardName: 'Second', boardUrl: 'second', sortOrder: 1 }),
      createBoard({ boardId: 1, boardName: 'First', boardUrl: 'first', sortOrder: 2 }),
    ])
    await firstDragSave

    expect(editor.isSavingSortOrder.value).toBe(false)
  })
})
