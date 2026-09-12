import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import {
  findButtonByText,
  mockCreateMutate,
  mockCreateScheduledMutate,
  mockGetPostSeries,
  mockPostFormAuthStore,
  mockSaveDraftMutateAsync,
  mockUpdateScheduledMutate,
  mountPostForm,
  resetPostFormTestState,
  scheduledPostRef,
  submitPostForm,
  unmountPostFormWrappers,
} from './PostFormTestHarness'
import PostEditorTipTap from '../PostEditorTipTap.vue'

type UploadResult = { data: { success: boolean, data: { fileId: number, fileUrl: string } } }
const uploadMocks = vi.hoisted(() => ({ uploadFile: vi.fn(), discardUploads: vi.fn() }))
vi.mock('@/api/file', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/file')>()
  return { ...actual, fileApi: { ...actual.fileApi, ...uploadMocks } }
})
vi.mock('@/stores/theme', () => ({ useThemeStore: () => ({ isDark: false }) }))

const pendingUploads: Array<(result: UploadResult) => void> = []

async function completeUpload(fileId: number) {
  const resolve = pendingUploads.shift()
  if (!resolve) throw new Error('Expected a pending upload')
  resolve({ data: { success: true, data: { fileId, fileUrl: `/api/v1/files/${fileId}` } } })
  await flushPromises()
}

describe('PostForm image uploads', () => {
  beforeEach(() => {
    resetPostFormTestState()
    pendingUploads.length = 0
    uploadMocks.uploadFile.mockImplementation((_file: File, config?: { signal?: AbortSignal }) => new Promise<UploadResult>((resolve, reject) => {
      pendingUploads.push(resolve)
      config?.signal?.addEventListener('abort', () => reject(new DOMException('Canceled', 'AbortError')), { once: true })
    }))
    uploadMocks.discardUploads.mockResolvedValue({ data: { data: { discardedCount: 0 } } })
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => 'blob:post-upload-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  })

  afterEach(() => {
    unmountPostFormWrappers()
    vi.restoreAllMocks()
  })

  it.each(['publish', 'schedule', 'update schedule'] as const)(
    'waits for every queued image before %s, including keyboard submission',
    async (action) => {
      scheduledPostRef.value = {
        scheduledPostId: 44,
        boardUrl: 'free',
        categoryId: 1,
        title: 'Photo post',
        contents: '',
        fileIds: [],
        scheduledAt: '2099-07-20T12:00',
      }
      const wrapper = mountPostForm(
        action === 'update schedule' ? 'edit' : 'create',
        { PostEditorTipTap },
        {},
        action === 'update schedule' ? { scheduledPostId: '44', postId: '' } : {},
      )
      await flushPromises()
      await wrapper.get('#title').setValue('Photo post')
      await wrapper.get('#category').setValue('1')
      if (action === 'schedule') await wrapper.get('#scheduled-at').setValue('2099-07-20T12:00')
      const input = wrapper.get('input[type="file"]')
      Object.defineProperty(input.element, 'files', {
        value: ['first.png', 'second.png'].map((name) => new File(['image'], name, { type: 'image/png' })),
      })
      await input.trigger('change')
      await flushPromises()

      expect(uploadMocks.uploadFile).toHaveBeenCalledTimes(1)
      expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(true)
      await submitPostForm(wrapper)
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, cancelable: true }))
      await flushPromises()
      expect(mockCreateMutate).not.toHaveBeenCalled()
      expect(mockCreateScheduledMutate).not.toHaveBeenCalled()
      expect(mockUpdateScheduledMutate).not.toHaveBeenCalled()

      await completeUpload(501)
      expect(uploadMocks.uploadFile).toHaveBeenCalledTimes(2)
      expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(true)
      await submitPostForm(wrapper)
      expect(mockCreateMutate).not.toHaveBeenCalled()
      expect(mockCreateScheduledMutate).not.toHaveBeenCalled()
      expect(mockUpdateScheduledMutate).not.toHaveBeenCalled()

      await completeUpload(502)
      expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(false)
      await submitPostForm(wrapper)
      const mutation = action === 'publish' ? mockCreateMutate
        : action === 'schedule' ? mockCreateScheduledMutate : mockUpdateScheduledMutate
      expect(mutation).toHaveBeenCalledTimes(1)
      const payload = mutation.mock.calls[0]?.[0].data
      expect(payload.fileIds).toEqual([501, 502])
      expect(payload.contents).toContain('/api/v1/files/501')
      expect(payload.contents).toContain('/api/v1/files/502')
    },
  )

  it.each(['failure', 'cancel'] as const)('allows publishing after upload %s', async (outcome) => {
    if (outcome === 'failure') uploadMocks.uploadFile.mockRejectedValueOnce(new Error('Upload failed'))
    const wrapper = mountPostForm('create', { PostEditorTipTap })
    await flushPromises()
    await wrapper.get('#title').setValue('Text post')
    await wrapper.get('#category').setValue('1')
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['image'], 'photo.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()
    if (outcome === 'cancel') {
      await findButtonByText(wrapper, 'board.writePost.upload.cancel').trigger('click')
      await flushPromises()
    }
    expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(false)
    await submitPostForm(wrapper)
    expect(mockCreateMutate).toHaveBeenCalledTimes(1)
  })

  it('blocks draft saving from buttons and keyboard until uploads finish', async () => {
    mockPostFormAuthStore({ isAuthenticated: true })
    mockGetPostSeries.mockResolvedValue({ data: { data: [] } })
    const wrapper = mountPostForm('create', { PostEditorTipTap })
    await flushPromises()
    await wrapper.get('#title').setValue('Photo draft')
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['image'], 'draft.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()
    mockSaveDraftMutateAsync.mockClear()
    const saveButton = findButtonByText(wrapper, 'board.writePost.actions.saveDraft')
    expect(saveButton.attributes('disabled')).toBeDefined()
    await saveButton.trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', ctrlKey: true, cancelable: true }))
    await flushPromises()
    expect(mockSaveDraftMutateAsync).not.toHaveBeenCalled()

    await completeUpload(504)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', ctrlKey: true, cancelable: true }))
    await flushPromises()
    expect(mockSaveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ fileIds: [504] }))
  })

  it('clears the pending state when switching out of the uploading editor', async () => {
    const wrapper = mountPostForm('create', { PostEditorTipTap })
    await flushPromises()
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['image'], 'photo.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(true)

    await findButtonByText(wrapper, 'board.writePost.viewHtmlSource').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'PostFormHeader' }).props('submitDisabled')).toBe(false)
    await completeUpload(503)
  })
})
