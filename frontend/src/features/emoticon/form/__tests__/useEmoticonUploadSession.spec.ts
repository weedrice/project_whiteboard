import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useEmoticonUploadSession } from '../useEmoticonUploadSession'

const mocks = vi.hoisted(() => ({
  discardUploads: vi.fn(),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    discardUploads: mocks.discardUploads,
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
    vi.clearAllMocks()
    mocks.discardUploads.mockResolvedValue({ data: { data: { discardedCount: 2 } } })
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
    })
    expect(session.trackedUploadedFileIds.value).toEqual([12])
  })
})
