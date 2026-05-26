import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useEmoticonUploadSession } from '../useEmoticonUploadSession'

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
})
