import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUserBlockAction } from '../useUserBlockAction'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  confirm: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: mocks.confirm }),
}))

vi.mock('@/utils/logger', () => ({
  default: { error: mocks.loggerError },
}))

describe('useUserBlockAction', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
  })

  it('runs the action and optional success side effects after confirmation', async () => {
    const action = vi.fn().mockResolvedValue({ ok: true })
    const onSuccess = vi.fn()
    const { runUserBlockAction } = useUserBlockAction()

    const result = await runUserBlockAction({
      action,
      confirmMessage: 'confirm',
      failureMessage: 'failed',
      logMessage: 'log:',
      successMessage: 'success',
      onSuccess,
    })

    expect(result).toBe(true)
    expect(action).toHaveBeenCalledTimes(1)
    expect(mocks.addToast).toHaveBeenCalledWith('success', 'success')
    expect(onSuccess).toHaveBeenCalledWith({ ok: true })
  })

  it('skips action when confirmation is cancelled', async () => {
    mocks.confirm.mockResolvedValueOnce(false)
    const action = vi.fn()
    const { runUserBlockAction } = useUserBlockAction()

    const result = await runUserBlockAction({
      action,
      confirmMessage: 'confirm',
      failureMessage: 'failed',
      logMessage: 'log:',
    })

    expect(result).toBe(false)
    expect(action).not.toHaveBeenCalled()
  })

  it('skips action when its session intent became stale during confirmation', async () => {
    const action = vi.fn()
    const { runUserBlockAction } = useUserBlockAction()

    const result = await runUserBlockAction({
      action,
      confirmMessage: 'confirm',
      failureMessage: 'failed',
      isIntentCurrent: () => false,
      logMessage: 'log:',
    })

    expect(result).toBe(false)
    expect(action).not.toHaveBeenCalled()
  })

  it('logs and shows failure toast when the action throws', async () => {
    const error = new Error('fail')
    const { runUserBlockAction } = useUserBlockAction()

    const result = await runUserBlockAction({
      action: vi.fn().mockRejectedValue(error),
      confirmMessage: 'confirm',
      failureMessage: 'failed',
      logMessage: 'log:',
    })

    expect(result).toBe(false)
    expect(mocks.loggerError).toHaveBeenCalledWith('log:', error)
    expect(mocks.addToast).toHaveBeenCalledWith('failed', 'error')
  })

  it('suppresses a stale failure after the session changes during the request', async () => {
    let isCurrent = true
    const { runUserBlockAction } = useUserBlockAction()
    const action = vi.fn().mockImplementation(async () => {
      isCurrent = false
      throw new Error('stale failure')
    })

    const result = await runUserBlockAction({
      action,
      confirmMessage: 'confirm',
      failureMessage: 'failed',
      isIntentCurrent: () => isCurrent,
      logMessage: 'log:',
    })

    expect(result).toBe(false)
    expect(mocks.loggerError).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalled()
  })
})
