import { defineComponent, h, nextTick, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useBoardIconUpload, validateBoardIconFile } from '../useBoardIconUpload'

const uploadFileMock = vi.hoisted(() => vi.fn())
const discardUploadsMock = vi.hoisted(() => vi.fn())
const addToastMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: uploadFileMock,
    discardUploads: discardUploadsMock,
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

function createHarness(ownerKey?: Ref<number | null>) {
  const setIconUrl = vi.fn()
  let composable!: ReturnType<typeof useBoardIconUpload>
  const Harness = defineComponent({
    setup() {
      composable = useBoardIconUpload({
        setIconUrl,
        ...(ownerKey ? { ownerKey: () => ownerKey.value } : {}),
      })
      return () => h('div')
    },
  })

  const wrapper = mount(Harness, {
    global: { plugins: [createPinia()] },
  })

  return {
    composable,
    setIconUrl,
    wrapper,
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

    expect(uploadFileMock).toHaveBeenCalledWith(file, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(setIconUrl).toHaveBeenCalledWith('/api/v1/files/10')
    expect(input.value).toBe('')
  })

  it('aborts the previous request and discards its late uploaded file', async () => {
    const { composable, setIconUrl } = createHarness()
    let resolveFirst!: (value: unknown) => void
    uploadFileMock
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockResolvedValueOnce({ data: { success: true, data: { fileId: 12, fileUrl: '/api/v1/files/12' } } })
    discardUploadsMock.mockResolvedValue({ data: { success: true, data: { discardedCount: 1 } } })

    const first = composable.handleFileUpload(createInputEvent(new File(['1'], 'one.png', { type: 'image/png' })).event)
    const firstSignal = uploadFileMock.mock.calls[0][1].signal as AbortSignal
    await composable.handleFileUpload(createInputEvent(new File(['2'], 'two.png', { type: 'image/png' })).event)

    expect(firstSignal.aborted).toBe(true)
    resolveFirst({ data: { success: true, data: { fileId: 11, fileUrl: '/api/v1/files/11' } } })
    await first
    await nextTick()

    expect(setIconUrl).toHaveBeenCalledTimes(1)
    expect(setIconUrl).toHaveBeenCalledWith('/api/v1/files/12')
    expect(discardUploadsMock).toHaveBeenCalledWith([11], expect.objectContaining({ skipGlobalErrorHandler: true }))
  })

  it('aborts an in-flight upload when its scope is unmounted', async () => {
    uploadFileMock.mockReturnValueOnce(new Promise(() => undefined))
    const { composable, wrapper } = createHarness()
    void composable.handleFileUpload(createInputEvent(new File(['x'], 'icon.png', { type: 'image/png' })).event)
    const signal = uploadFileMock.mock.calls[0][1].signal as AbortSignal

    wrapper.unmount()

    expect(signal.aborted).toBe(true)
  })

  it('aborts and discards a late upload from the previous session generation', async () => {
    let resolveUpload!: (value: unknown) => void
    uploadFileMock.mockReturnValueOnce(new Promise(resolve => { resolveUpload = resolve }))
    discardUploadsMock.mockResolvedValue({ data: { success: true, data: { discardedCount: 1 } } })
    const { composable, setIconUrl } = createHarness()
    const pending = composable.handleFileUpload(
      createInputEvent(new File(['x'], 'icon.png', { type: 'image/png' })).event,
    )
    const signal = uploadFileMock.mock.calls[0][1].signal as AbortSignal

    useAuthStore().setTokens('next-session')
    expect(signal.aborted).toBe(true)
    resolveUpload({ data: { success: true, data: { fileId: 13, fileUrl: '/api/v1/files/13' } } })
    await pending
    await nextTick()

    expect(setIconUrl).not.toHaveBeenCalled()
    expect(discardUploadsMock).toHaveBeenCalledWith([13], expect.objectContaining({ skipGlobalErrorHandler: true }))
  })

  it('aborts and discards an upload when the selected board changes', async () => {
    let resolveUpload!: (value: unknown) => void
    uploadFileMock.mockReturnValueOnce(new Promise(resolve => { resolveUpload = resolve }))
    discardUploadsMock.mockResolvedValue({ data: { success: true, data: { discardedCount: 1 } } })
    const ownerKey = ref<number | null>(1)
    const { composable, setIconUrl } = createHarness(ownerKey)
    const pending = composable.handleFileUpload(
      createInputEvent(new File(['x'], 'icon.png', { type: 'image/png' })).event,
    )
    const signal = uploadFileMock.mock.calls[0][1].signal as AbortSignal

    ownerKey.value = 2
    expect(signal.aborted).toBe(true)
    resolveUpload({ data: { success: true, data: { fileId: 14, fileUrl: '/api/v1/files/14' } } })
    await pending
    await nextTick()

    expect(setIconUrl).not.toHaveBeenCalled()
    expect(discardUploadsMock).toHaveBeenCalledWith([14], expect.objectContaining({ skipGlobalErrorHandler: true }))
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
