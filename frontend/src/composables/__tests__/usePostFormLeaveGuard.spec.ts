import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostFormLeaveGuard } from '@/features/board/posts/form/usePostFormLeaveGuard'

const routeGuard = vi.hoisted(() => ({
  leaveCallback: undefined as undefined | (() => boolean | Promise<boolean>),
  updateCallback: undefined as undefined | (() => boolean | Promise<boolean>),
}))

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn((callback) => {
    routeGuard.leaveCallback = callback
  }),
  onBeforeRouteUpdate: vi.fn((callback) => {
    routeGuard.updateCallback = callback
  }),
}))

const runLeaveGuard = async () => routeGuard.leaveCallback?.()
const runUpdateGuard = async () => routeGuard.updateCallback?.()

function createForm(options: Partial<{
  dirty: boolean
  submitting: boolean
  message: string
  flushResult: boolean
}> = {}) {
  const flushDraft = vi.fn().mockReturnValue(options.flushResult ?? true)
  return {
    target: {
      getLeaveState: () => ({
        dirty: options.dirty ?? false,
        submitting: options.submitting ?? false,
        message: options.message ?? '',
      }),
      flushDraft,
    },
    flushDraft,
  }
}

describe('usePostFormLeaveGuard', () => {
  it('continues without flushing when the form is clean', async () => {
    const form = createForm()
    const confirmLeave = vi.fn()

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(true)
    expect(confirmLeave).not.toHaveBeenCalled()
    expect(form.flushDraft).not.toHaveBeenCalled()
  })

  it('blocks navigation without prompting while submission is in progress', async () => {
    const form = createForm({ dirty: true, submitting: true })
    const confirmLeave = vi.fn().mockResolvedValue(true)

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(false)
    expect(confirmLeave).not.toHaveBeenCalled()
    expect(form.flushDraft).not.toHaveBeenCalled()
  })

  it('allows success navigation after submission resets the shared leave state', async () => {
    const form = createForm({ dirty: false, submitting: false })
    const confirmLeave = vi.fn()

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(true)
    expect(confirmLeave).not.toHaveBeenCalled()
  })

  it('blocks navigation when confirmation is rejected', async () => {
    const form = createForm({ dirty: true, message: 'custom message' })
    const confirmLeave = vi.fn().mockResolvedValue(false)

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(false)
    expect(confirmLeave).toHaveBeenCalledWith('custom message')
    expect(form.flushDraft).not.toHaveBeenCalled()
  })

  it('uses the fallback message and flushes before allowing navigation', async () => {
    const form = createForm({ dirty: true })
    const confirmLeave = vi.fn().mockReturnValue(true)

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(true)
    expect(confirmLeave).toHaveBeenCalledWith('fallback')
    expect(form.flushDraft).toHaveBeenCalledOnce()
  })

  it('guards same-route updates with the same leave state contract', async () => {
    const form = createForm({ dirty: true, message: 'same route' })
    const confirmLeave = vi.fn().mockResolvedValue(true)

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runUpdateGuard()).toBe(true)
    expect(confirmLeave).toHaveBeenCalledWith('same route')
    expect(form.flushDraft).toHaveBeenCalledOnce()
  })

  it('blocks navigation when the latest local draft cannot be flushed', async () => {
    const form = createForm({ dirty: true, flushResult: false })
    const confirmLeave = vi.fn().mockResolvedValue(true)

    usePostFormLeaveGuard(ref(form.target), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(false)
    expect(form.flushDraft).toHaveBeenCalledOnce()
  })

  it('allows navigation while the form ref is not mounted', async () => {
    const confirmLeave = vi.fn()

    usePostFormLeaveGuard(ref(null), 'fallback', confirmLeave)

    expect(await runLeaveGuard()).toBe(true)
    expect(confirmLeave).not.toHaveBeenCalled()
  })
})
