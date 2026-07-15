import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardIconUpload, validateBoardIconFile } from '../useBoardIconUpload'

const uploadFileMock = vi.hoisted(() => vi.fn())
const addToastMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: uploadFileMock,
  },
  resolveFileUploadUrl: (uploadedFile: { url?: string; fileUrl?: string }) => uploadedFile.url ?? uploadedFile.fileUrl ?? null,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: addToastMock,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function createHarness() {
  const setIconUrl = vi.fn()
  let composable!: ReturnType<typeof useBoardIconUpload>
  const Harness = defineComponent({
    setup() {
      composable = useBoardIconUpload({ setIconUrl })
      return () => h('div')
    },
  })

  mount(Harness)

  return {
    composable,
    setIconUrl,
  }
}

function createInputEvent(file: File) {
  const input = document.createElement('input')
  input.type = 'file'
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file],
  })

  return {
    input,
    event: { target: input } as unknown as Event,
  }
}

describe('useBoardIconUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('validates board icon mime type, extension and size', () => {
    expect(validateBoardIconFile(new File(['x'], 'icon.svg', { type: 'image/svg+xml' }))).toBe('type')
    expect(validateBoardIconFile(new File(['x'], 'icon.png', { type: 'image/svg+xml' }))).toBe('type')
    expect(validateBoardIconFile(new File([new ArrayBuffer(3)], 'icon.png', { type: 'image/png' }), 2)).toBe('size')
    expect(validateBoardIconFile(new File(['x'], 'icon.webp', { type: 'image/webp' }))).toBeNull()
  })

  it('uploads a valid icon, applies the resolved url and resets the file input', async () => {
    const { composable, setIconUrl } = createHarness()
    const file = new File(['x'], 'icon.png', { type: 'image/png' })
    const { event, input } = createInputEvent(file)

    uploadFileMock.mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          fileUrl: '/api/v1/files/10',
        },
      },
    })

    await composable.handleFileUpload(event)
    await nextTick()

    expect(uploadFileMock).toHaveBeenCalledWith(file)
    expect(setIconUrl).toHaveBeenCalledWith('/api/v1/files/10')
    expect(input.value).toBe('')
  })

  it('blocks invalid icons before upload', async () => {
    const { composable } = createHarness()
    const invalidType = createInputEvent(new File(['x'], 'icon.svg', { type: 'image/svg+xml' }))
    const tooLarge = createInputEvent(new File([new ArrayBuffer(2 * 1024 * 1024 + 1)], 'icon.png', { type: 'image/png' }))

    await composable.handleFileUpload(invalidType.event)
    expect(addToastMock).toHaveBeenCalledWith('board.form.invalidIconType', 'error')

    await composable.handleFileUpload(tooLarge.event)
    expect(addToastMock).toHaveBeenCalledWith('board.form.iconTooLarge', 'error')
    expect(uploadFileMock).not.toHaveBeenCalled()
  })
})
