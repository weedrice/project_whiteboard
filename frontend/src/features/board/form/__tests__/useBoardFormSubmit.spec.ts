import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardFormSubmit } from '../useBoardFormSubmit'
import type { BoardFormData } from '../useBoardFormState'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  handleError: vi.fn(),
  uploadBoardIconFile: vi.fn(),
  validateBoardIconFile: vi.fn(),
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

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleError: mocks.handleError,
  }),
}))

vi.mock('@/features/board/icons/useBoardIconUpload', () => ({
  uploadBoardIconFile: mocks.uploadBoardIconFile,
  validateBoardIconFile: mocks.validateBoardIconFile,
}))

function createForm(overrides: Partial<BoardFormData> = {}): BoardFormData {
  return {
    boardName: 'Board',
    boardUrl: 'board',
    description: '',
    iconUrl: '',
    sortOrder: 0,
    allowNsfw: false,
    isPublic: true,
    agentUseYn: false,
    guidePrompt: '',
    ...overrides,
  }
}

function createSubmitter(overrides: Partial<BoardFormData> = {}) {
  const emitSubmit = vi.fn()
  const form = ref(createForm(overrides))
  const submitter = useBoardFormSubmit({
    form,
    selectedFile: ref(null),
    isEdit: () => false,
    canCreate: computed(() => true),
    boardCreateCost: computed(() => 100),
    emitSubmit,
  })

  return { emitSubmit, form, submitter }
}

describe('useBoardFormSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.validateBoardIconFile.mockReturnValue(null)
    mocks.uploadBoardIconFile.mockResolvedValue(null)
  })

  it('emits a normalized board payload', async () => {
    const { emitSubmit, submitter } = createSubmitter({
      boardName: '  Free Board  ',
      boardUrl: '  free_board  ',
      description: '  description  ',
      iconUrl: '  https://example.com/icon.png  ',
      agentUseYn: true,
      guidePrompt: '  guide prompt  ',
    })

    await submitter.handleSubmit()

    expect(emitSubmit).toHaveBeenCalledWith(expect.objectContaining({
      boardName: 'Free Board',
      boardUrl: 'free_board',
      description: 'description',
      iconUrl: 'https://example.com/icon.png',
      agentUseYn: true,
      guidePrompt: 'guide prompt',
    }))
  })

  it('blocks whitespace-only required fields', async () => {
    const { emitSubmit, submitter } = createSubmitter({
      boardName: '   ',
      boardUrl: 'board',
    })

    await submitter.handleSubmit()

    expect(emitSubmit).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('board.form.validation', 'error')
  })

  it('forces agent usage off for private boards', async () => {
    const { emitSubmit, submitter } = createSubmitter({
      isPublic: false,
      agentUseYn: true,
      guidePrompt: '  private guide  ',
    })

    await submitter.handleSubmit()

    expect(emitSubmit).toHaveBeenCalledWith(expect.objectContaining({
      isPublic: false,
      agentUseYn: false,
      guidePrompt: 'private guide',
    }))
  })
})
