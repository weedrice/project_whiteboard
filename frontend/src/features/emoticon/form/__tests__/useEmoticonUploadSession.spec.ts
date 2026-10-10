import { useAuthStore } from '@/stores/auth'
import { createDeferred } from '@/test/async'
import { useEmoticonImageUploader } from '../useEmoticonImageUploader'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useEmoticonUploadSession } from '../useEmoticonUploadSession'

const mocks = vi.hoisted(() => ({
  discardUploads: vi.fn(),
  uploadFile: vi.fn(),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    discardUploads: mocks.discardUploads,
    uploadFile: mocks.uploadFile,
  },
}))

function mountSession() {
  const holder: { session?: ReturnType<typeof useEmoticonUploadSession> } = {}

  const TestHarness = defineComponent({
    setup() {
      holder.session = useEmoticonUploadSession()
      return () => h('div')
    },
  })

  const wrapper = mount(TestHarness)
  if (!holder.session) throw new Error('session not mounted')
  return { wrapper, session: holder.session }
}

describe('useEmoticonUploadSession', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.discardUploads.mockResolvedValue({ data: { data: { discardedCount: 2 } } })
  })

  it('does not abort a new run when an old session upload fails late', async () => {
    const { session } = mountSession()
    const uploader = useEmoticonImageUploader(session)
    const previous = createDeferred<unknown>()
    const next = createDeferred<unknown>()
    mocks.uploadFile.mockReturnValueOnce(previous.promise).mockReturnValueOnce(next.promise)
    const file = new File(['image'], 'image.png', { type: 'image/png' })
    const previousRun = session.startSubmitRun()
    const previousUpload = uploader.uploadFile(file, previousRun)
    useAuthStore().sessionGeneration += 1
    const nextRun = session.startSubmitRun()
    const nextUpload = uploader.uploadFile(file, nextRun)
    const nextConfig = mocks.uploadFile.mock.calls[1]?.[1]
    previous.reject(new Error('old upload failed'))
    await expect(previousUpload).rejects.toThrow('old upload failed')
    expect(nextConfig.signal.aborted).toBe(false)
    next.resolve({ data: { success: true, data: { fileId: 12 } } })
    await expect(nextUpload).resolves.toBe(12)
    expect(session.isSubmitActive(nextRun)).toBe(true)
    session.finishSubmitRun(previousRun)
    session.finishSubmitRun(nextRun)
  })

  it('tracks progress and cancels active upload controllers on unmount', () => {
    const { wrapper, session } = mountSession()
    const controller = session.createUploadController()

    session.setUploadProgress(1, 2)

    expect(session.uploadProgress.value).toEqual({ current: 1, total: 2 })
    expect(controller.signal.aborted).toBe(false)

    wrapper.unmount()

    expect(controller.signal.aborted).toBe(true)
    expect(session.isDisposed.value).toBe(true)
  })

  it('invalidates a submit run when it is cancelled', () => {
    const { session } = mountSession()
    const runId = session.startSubmitRun()
    const controller = session.createUploadController()

    expect(session.isSubmitActive(runId)).toBe(true)

    session.cancelSubmitRun()

    expect(controller.signal.aborted).toBe(true)
    expect(session.isSubmitActive(runId)).toBe(false)
    expect(() => session.assertSubmitActive(runId)).toThrow(DOMException)
  })

  it('recognizes abort-shaped upload cancellation errors', () => {
    const { session } = mountSession()

    expect(session.isUploadCancelledError(new DOMException('cancelled', 'AbortError'))).toBe(true)
    expect(session.isUploadCancelledError({ code: 'ERR_CANCELED' })).toBe(true)
    expect(session.isUploadCancelledError(new Error('network'))).toBe(false)
  })

  it('deduplicates and discards tracked uploads before clearing them', async () => {
    const { session } = mountSession()

    session.recordUploadedFile(11)
    session.recordUploadedFile(11)
    session.recordUploadedFile(12)

    await session.discardTrackedUploads()

    expect(mocks.discardUploads).toHaveBeenCalledWith([11, 12], {
      skipGlobalErrorHandler: true,
      expectedSession: { sessionGeneration: 0, userId: null },
      signal: expect.any(AbortSignal),
    })
    expect(session.trackedUploadedFileIds.value).toEqual([])
  })

  it('keeps final-request uploads owned without preserving stale UI intent after unmount', async () => {
    const { wrapper, session } = mountSession()
    const runId = session.startSubmitRun()
    session.recordUploadedFile(11, runId)
    session.beginFinalRequest(runId)

    wrapper.unmount()
    await Promise.resolve()

    expect(session.isDisposed.value).toBe(true)
    expect(session.isSubmitActive(runId)).toBe(false)
    expect(mocks.discardUploads).not.toHaveBeenCalled()

    session.clearTrackedUploads(runId)
    session.finishFinalRequest(runId)
    expect(session.isSubmitActive(runId)).toBe(false)
  })

  it('does not discard uploads owned by a newer submit run', async () => {
    const { session } = mountSession()
    const oldRunId = session.startSubmitRun()
    session.recordUploadedFile(11, oldRunId)
    const newRunId = session.startSubmitRun()
    session.recordUploadedFile(12, newRunId)

    await session.discardTrackedUploads(oldRunId)

    expect(mocks.discardUploads).toHaveBeenCalledWith([11], {
      skipGlobalErrorHandler: true,
      expectedSession: { sessionGeneration: 0, userId: null },
      signal: expect.any(AbortSignal),
    })
    expect(session.trackedUploadedFileIds.value).toEqual([12])
  })
})
