import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardFormSubmit, type BoardFormSubmitAction } from '../useBoardFormSubmit'
import type { BoardFormData } from '../useBoardFormState'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  discardUploads: vi.fn(),
  handleError: vi.fn(),
  uploadBoardIconFile: vi.fn(),
  validateBoardIconFile: vi.fn(),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    discardUploads: mocks.discardUploads,
  },
  resolveFileUploadUrl: (uploadedFile: { url?: string; fileUrl?: string }) => (
    uploadedFile.url ?? uploadedFile.fileUrl ?? null
  ),
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

function createSubmitter(
  overrides: Partial<BoardFormData> = {},
  options: {
    selectedFile?: File | null
    submitAction?: BoardFormSubmitAction
    getSessionGeneration?: () => number
  } = {},
) {
  const emitSubmit = vi.fn()
  const form = ref(createForm(overrides))
  const selectedFile = ref(options.selectedFile ?? null)
  const submitter = useBoardFormSubmit({
    form,
    selectedFile,
    isEdit: () => false,
    canCreate: computed(() => true),
    boardCreateCost: computed(() => 100),
    submitAction: options.submitAction,
    emitSubmit,
    getSessionGeneration: options.getSessionGeneration,
  })

  return { emitSubmit, form, selectedFile, submitter }
}

describe('useBoardFormSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.validateBoardIconFile.mockReturnValue(null)
    mocks.uploadBoardIconFile.mockResolvedValue(null)
    mocks.discardUploads.mockResolvedValue({ data: { success: true, data: { discardedCount: 1 } } })
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

  it('validates backend text limits before uploading an icon', async () => {
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    const submitAction = vi.fn().mockResolvedValue(true)
    const { submitter } = createSubmitter(
      { boardName: 'x'.repeat(101) },
      { selectedFile: file, submitAction },
    )

    await submitter.handleSubmit()

    expect(mocks.uploadBoardIconFile).not.toHaveBeenCalled()
    expect(submitAction).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('board.form.validation', 'error')
  })

  it('keeps an uploaded icon when the awaited board mutation succeeds', async () => {
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    mocks.uploadBoardIconFile.mockResolvedValue({ fileId: 41, fileUrl: '/api/v1/files/41' })
    const submitAction = vi.fn().mockImplementation(async (_payload, context) => {
      context.commitUpload()
      return true
    })
    const { submitter } = createSubmitter({}, { selectedFile: file, submitAction })

    await submitter.handleSubmit()

    expect(mocks.uploadBoardIconFile).toHaveBeenCalledWith(file, {
      signal: expect.any(AbortSignal),
    })
    expect(submitAction).toHaveBeenCalledWith(
      expect.objectContaining({ iconUrl: '/api/v1/files/41' }),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(mocks.discardUploads).not.toHaveBeenCalled()
  })

  it('discards an uploaded icon when the awaited board mutation fails', async () => {
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    mocks.uploadBoardIconFile.mockResolvedValue({ fileId: 42, url: '/api/v1/files/42' })
    const submitAction = vi.fn().mockResolvedValue(false)
    const { submitter } = createSubmitter({}, { selectedFile: file, submitAction })

    await submitter.handleSubmit()

    expect(mocks.discardUploads).toHaveBeenCalledWith([42], {
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
    })
  })

  it('aborts and discards an uploaded icon when submission is cancelled', async () => {
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    mocks.uploadBoardIconFile.mockResolvedValue({ fileId: 43, url: '/api/v1/files/43' })
    let resolveMutation!: (value: boolean) => void
    const submitAction = vi.fn().mockReturnValue(new Promise<boolean>((resolve) => {
      resolveMutation = resolve
    }))
    const { submitter } = createSubmitter({}, { selectedFile: file, submitAction })
    const pending = submitter.handleSubmit()
    await vi.waitFor(() => expect(submitAction).toHaveBeenCalledOnce())
    const signal = submitAction.mock.calls[0][1].signal as AbortSignal

    submitter.cancelSubmission()
    resolveMutation(false)
    await pending

    expect(signal.aborted).toBe(true)
    expect(mocks.discardUploads).toHaveBeenCalledTimes(1)
    expect(mocks.discardUploads).toHaveBeenCalledWith([43], expect.any(Object))
  })

  it('aborts and discards an uploaded icon when the auth session changes', async () => {
    const file = new File(['icon'], 'icon.png', { type: 'image/png' })
    mocks.uploadBoardIconFile.mockResolvedValue({ fileId: 44, url: '/api/v1/files/44' })
    const generation = ref(0)
    let resolveMutation!: (value: boolean) => void
    const submitAction = vi.fn().mockReturnValue(new Promise<boolean>((resolve) => {
      resolveMutation = resolve
    }))
    const { submitter } = createSubmitter({}, {
      selectedFile: file,
      submitAction,
      getSessionGeneration: () => generation.value,
    })
    const pending = submitter.handleSubmit()
    await vi.waitFor(() => expect(submitAction).toHaveBeenCalledOnce())

    generation.value += 1
    resolveMutation(false)
    await pending

    expect((submitAction.mock.calls[0][1].signal as AbortSignal).aborted).toBe(true)
    expect(mocks.discardUploads).toHaveBeenCalledWith([44], expect.any(Object))
  })

  it('aborts and discards the pending upload when the selected icon is replaced', async () => {
    const firstFile = new File(['first'], 'first.png', { type: 'image/png' })
    mocks.uploadBoardIconFile.mockResolvedValue({ fileId: 45, url: '/api/v1/files/45' })
    let resolveMutation!: (value: boolean) => void
    const submitAction = vi.fn().mockReturnValue(new Promise<boolean>((resolve) => {
      resolveMutation = resolve
    }))
    const { selectedFile, submitter } = createSubmitter({}, {
      selectedFile: firstFile,
      submitAction,
    })
    const pending = submitter.handleSubmit()
    await vi.waitFor(() => expect(submitAction).toHaveBeenCalledOnce())

    selectedFile.value = new File(['second'], 'second.png', { type: 'image/png' })
    resolveMutation(false)
    await pending

    expect((submitAction.mock.calls[0][1].signal as AbortSignal).aborted).toBe(true)
    expect(mocks.discardUploads).toHaveBeenCalledWith([45], expect.any(Object))
  })

  it('blocks creation when the canonical creation cost is unavailable', async () => {
    const emitSubmit = vi.fn()
    const submitter = useBoardFormSubmit({
      form: ref(createForm()),
      selectedFile: ref(null),
      isEdit: () => false,
      canCreate: computed(() => false),
      boardCreateCost: computed(() => null),
      emitSubmit,
    })

    await submitter.handleSubmit()

    expect(emitSubmit).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('board.form.createCostUnavailable', 'error')
  })
})
