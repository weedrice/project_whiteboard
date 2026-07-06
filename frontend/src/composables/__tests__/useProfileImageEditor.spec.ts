import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useProfileImageEditor } from '../useProfileImageEditor'
import { createDeferred } from '@/test/async'

const resizeImageToBoundsFileMock = vi.hoisted(() => vi.fn())
const validateImageFileMock = vi.hoisted(() => vi.fn<() => 'type' | 'size' | null>(() => null))

vi.mock('@/utils/imageFile', () => ({
  resizeImageToBoundsFile: resizeImageToBoundsFileMock,
  validateImageFile: validateImageFileMock,
}))

vi.mock('@/utils/image', () => ({
  getOptimizedProfileImageUrl: (url: string) => url,
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

function createHarness() {
  const onFileSizeExceeded = vi.fn()
  const onProcessFailed = vi.fn()
  let composable!: ReturnType<typeof useProfileImageEditor>

  const Harness = defineComponent({
    setup() {
      composable = useProfileImageEditor({
        profileImageUrl: () => null,
        onFileSizeExceeded,
        onProcessFailed,
      })
      return () => h('div')
    },
  })

  mount(Harness)

  return {
    composable,
    onFileSizeExceeded,
    onProcessFailed,
  }
}

function createInputEvent(file: File) {
  const input = document.createElement('input')
  input.type = 'file'
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file],
  })
  Object.defineProperty(input, 'value', {
    configurable: true,
    writable: true,
    value: 'C:\\fakepath\\avatar.png',
  })

  return {
    input,
    event: { target: input } as unknown as Event,
  }
}

describe('useProfileImageEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    validateImageFileMock.mockReturnValue(null)
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => `blob:${(blob as File).name || 'preview'}`)
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  it('resizes a selected profile image and resets the file input', async () => {
    const { composable } = createHarness()
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const resizedFile = new File(['resized'], 'avatar-resized.png', { type: 'image/png' })
    const { event, input } = createInputEvent(file)

    resizeImageToBoundsFileMock.mockResolvedValueOnce(resizedFile)

    await composable.handleFileChange(event)

    expect(input.value).toBe('')
    expect(validateImageFileMock).toHaveBeenCalled()
    expect(resizeImageToBoundsFileMock).toHaveBeenCalledWith(file, 100, 100)
    expect(composable.selectedFile.value).toBe(resizedFile)
    expect(composable.profileImageDisplayUrl.value).toBe('blob:avatar-resized.png')
  })

  it('resets the file input before blocking oversized images', async () => {
    const { composable, onFileSizeExceeded } = createHarness()
    const tooLargeFile = new File([new ArrayBuffer(10 * 1024 * 1024 + 1)], 'avatar.png', {
      type: 'image/png',
    })
    const { event, input } = createInputEvent(tooLargeFile)
    validateImageFileMock.mockReturnValueOnce('size')

    await composable.handleFileChange(event)

    expect(input.value).toBe('')
    expect(onFileSizeExceeded).toHaveBeenCalledTimes(1)
    expect(resizeImageToBoundsFileMock).not.toHaveBeenCalled()
  })

  it('blocks unsupported profile image types before resizing', async () => {
    const { composable, onProcessFailed } = createHarness()
    const svgFile = new File(['<svg></svg>'], 'avatar.svg', {
      type: 'image/svg+xml',
    })
    const { event, input } = createInputEvent(svgFile)
    validateImageFileMock.mockReturnValueOnce('type')

    await composable.handleFileChange(event)

    expect(input.value).toBe('')
    expect(onProcessFailed).toHaveBeenCalledTimes(1)
    expect(resizeImageToBoundsFileMock).not.toHaveBeenCalled()
  })

  it('keeps the latest selected image when resize calls resolve out of order', async () => {
    const { composable } = createHarness()
    const firstFile = new File(['first'], 'first.png', { type: 'image/png' })
    const secondFile = new File(['second'], 'second.png', { type: 'image/png' })
    const firstResized = new File(['first-resized'], 'first-resized.png', { type: 'image/png' })
    const secondResized = new File(['second-resized'], 'second-resized.png', { type: 'image/png' })
    const firstResize = createDeferred<File>()
    const secondResize = createDeferred<File>()
    resizeImageToBoundsFileMock
      .mockReturnValueOnce(firstResize.promise)
      .mockReturnValueOnce(secondResize.promise)

    const firstSelection = composable.handleFileChange(createInputEvent(firstFile).event)
    const secondSelection = composable.handleFileChange(createInputEvent(secondFile).event)

    secondResize.resolve(secondResized)
    await secondSelection
    expect(composable.selectedFile.value).toBe(secondResized)
    expect(composable.profileImageDisplayUrl.value).toBe('blob:second-resized.png')

    firstResize.resolve(firstResized)
    await firstSelection

    expect(composable.selectedFile.value).toBe(secondResized)
    expect(composable.profileImageDisplayUrl.value).toBe('blob:second-resized.png')
  })
})
