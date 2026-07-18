import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminBoardCreateModal } from '../useAdminBoardCreateModal'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
}))

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })

  return { promise, reject, resolve }
}

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

describe('useAdminBoardCreateModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens with an empty create form', () => {
    const createBoard = vi.fn()
    const modal = useAdminBoardCreateModal(createBoard)
    Object.assign(modal.createForm, {
      boardName: 'Old',
      boardUrl: 'old',
      description: 'Old description',
    })

    modal.openCreateModal()

    expect(modal.isModalOpen.value).toBe(true)
    expect(modal.createForm).toEqual({
      boardName: '',
      boardUrl: '',
      description: '',
      iconUrl: '',
      agentUseYn: false,
      guidePrompt: '',
    })
  })

  it('normalizes create payload before submitting', async () => {
    const createBoard = vi.fn().mockResolvedValue(undefined)
    const modal = useAdminBoardCreateModal(createBoard)
    Object.assign(modal.createForm, {
      boardName: '  Free Board  ',
      boardUrl: 'Free-Board_1!',
      description: '  Description  ',
      iconUrl: '  https://example.com/icon.png  ',
      agentUseYn: true,
      guidePrompt: '  Guide prompt  ',
    })
    await nextTick()

    await modal.handleCreateBoard()

    expect(createBoard).toHaveBeenCalledWith(expect.objectContaining({
      boardName: 'Free Board',
      boardUrl: 'free-board_1',
      description: 'Description',
      iconUrl: 'https://example.com/icon.png',
      agentUseYn: true,
      guidePrompt: 'Guide prompt',
    }))
    expect(mocks.addToast).toHaveBeenCalledWith('admin.boards.messages.created', 'success')
    expect(modal.isModalOpen.value).toBe(false)
  })

  it('blocks create when required fields are blank', async () => {
    const createBoard = vi.fn()
    const modal = useAdminBoardCreateModal(createBoard)
    modal.createForm.boardName = '   '
    modal.createForm.boardUrl = 'board'

    await modal.handleCreateBoard()

    expect(createBoard).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('board.form.validation', 'error')
  })

  it('keeps the modal open and resets creating state when create fails', async () => {
    const createBoard = vi.fn().mockRejectedValue(new Error('failed'))
    const modal = useAdminBoardCreateModal(createBoard)
    modal.openCreateModal()
    Object.assign(modal.createForm, {
      boardName: 'Board',
      boardUrl: 'board',
    })

    await modal.handleCreateBoard()

    expect(modal.isModalOpen.value).toBe(true)
    expect(modal.isCreatingBoard.value).toBe(false)
    expect(mocks.addToast).not.toHaveBeenCalledWith('admin.boards.messages.created', 'success')
  })

  it('blocks create when a board field exceeds the backend contract', async () => {
    const createBoard = vi.fn()
    const modal = useAdminBoardCreateModal(createBoard)
    modal.createForm.boardName = 'a'.repeat(101)
    modal.createForm.boardUrl = 'board'

    await modal.handleCreateBoard()

    expect(createBoard).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('board.form.validation', 'error')
  })

  it('ignores a completed request from a previously closed modal instance', async () => {
    const firstRequest = deferred<void>()
    const createBoard = vi.fn().mockReturnValue(firstRequest.promise)
    const modal = useAdminBoardCreateModal(createBoard)
    modal.openCreateModal()
    Object.assign(modal.createForm, {
      boardName: 'Old board',
      boardUrl: 'old-board',
    })

    const pendingSubmit = modal.handleCreateBoard()
    modal.closeModal()
    modal.openCreateModal()
    Object.assign(modal.createForm, {
      boardName: 'New board',
      boardUrl: 'new-board',
    })
    firstRequest.resolve()
    await pendingSubmit

    expect(modal.isModalOpen.value).toBe(true)
    expect(modal.isCreatingBoard.value).toBe(false)
    expect(modal.createForm.boardName).toBe('New board')
    expect(mocks.addToast).not.toHaveBeenCalledWith('admin.boards.messages.created', 'success')
  })

  it('does not let an old request clear the creating state of a new request', async () => {
    const firstRequest = deferred<void>()
    const secondRequest = deferred<void>()
    const createBoard = vi.fn()
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    const modal = useAdminBoardCreateModal(createBoard)
    modal.openCreateModal()
    Object.assign(modal.createForm, { boardName: 'Old board', boardUrl: 'old-board' })
    const firstSubmit = modal.handleCreateBoard()

    modal.closeModal()
    modal.openCreateModal()
    Object.assign(modal.createForm, { boardName: 'New board', boardUrl: 'new-board' })
    const secondSubmit = modal.handleCreateBoard()
    firstRequest.resolve()
    await firstSubmit

    expect(modal.isModalOpen.value).toBe(true)
    expect(modal.isCreatingBoard.value).toBe(true)

    secondRequest.reject(new Error('failed'))
    await secondSubmit
    expect(modal.isCreatingBoard.value).toBe(false)
  })
})
