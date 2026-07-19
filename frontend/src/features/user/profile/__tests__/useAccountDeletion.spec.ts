import { describe, expect, it, vi } from 'vitest'
import { useAccountDeletion } from '../useAccountDeletion'

const mocks = vi.hoisted(() => ({
  loggerError: vi.fn(),
  extractValidationErrors: vi.fn(),
  getFieldError: vi.fn(),
  extractErrorMessage: vi.fn(),
}))

vi.mock('@/utils/logger', () => ({ default: { error: mocks.loggerError } }))
vi.mock('@/utils/errorHandler', () => ({
  extractValidationErrors: mocks.extractValidationErrors,
  getFieldError: mocks.getFieldError,
  extractErrorMessage: mocks.extractErrorMessage,
}))

function setup(deleteAccount = vi.fn().mockResolvedValue(undefined)) {
  let sessionGeneration = 0
  const options = {
    deleteAccount,
    logout: vi.fn().mockResolvedValue(undefined),
    pushHome: vi.fn().mockResolvedValue(undefined),
    getSessionGeneration: () => sessionGeneration,
    t: (key: string) => `t:${key}`,
  }
  return { result: useAccountDeletion(options), options, setSessionGeneration: (value: number) => { sessionGeneration = value } }
}

describe('useAccountDeletion', () => {
  it('requires a password before calling the API', async () => {
    const { result, options } = setup()
    result.showDeleteModal.value = true
    await result.handleDeleteAccount()
    expect(result.deleteError.value).toBe('t:auth.passwordRequired')
    expect(options.deleteAccount).not.toHaveBeenCalled()
  })

  it('deletes, closes, logs out, and navigates home in order', async () => {
    const { result, options } = setup()
    result.showDeleteModal.value = true
    result.deletePassword.value = 'password'
    await result.handleDeleteAccount()
    expect(options.deleteAccount).toHaveBeenCalledWith('password')
    expect(result.showDeleteModal.value).toBe(false)
    expect(options.logout).toHaveBeenCalled()
    expect(options.pushHome).toHaveBeenCalled()
  })

  it('shows a password field validation error', async () => {
    mocks.extractValidationErrors.mockReturnValue({ password: ['invalid'] })
    mocks.getFieldError.mockReturnValue('wrong password')
    const { result } = setup(vi.fn().mockRejectedValue(new Error('bad')))
    result.deletePassword.value = 'bad'
    result.showDeleteModal.value = true
    await result.handleDeleteAccount()
    expect(result.deleteError.value).toBe('wrong password')
    expect(mocks.loggerError).toHaveBeenCalled()
  })

  it('falls back to a translated generic error', async () => {
    mocks.extractValidationErrors.mockReturnValue(null)
    mocks.extractErrorMessage.mockReturnValue('')
    const { result } = setup(vi.fn().mockRejectedValue(new Error('bad')))
    result.deletePassword.value = 'bad'
    result.showDeleteModal.value = true
    await result.handleDeleteAccount()
    expect(result.deleteError.value).toBe('t:common.messages.error')
  })

  it('completes logout and navigation after deletion even if the modal revision changes', async () => {
    let resolveDelete!: () => void
    const request = new Promise<void>((resolve) => { resolveDelete = resolve })
    const { result, options } = setup(vi.fn(() => request))
    result.showDeleteModal.value = true
    result.deletePassword.value = 'secret'
    const pending = result.handleDeleteAccount()

    result.showDeleteModal.value = false
    resolveDelete()
    await pending

    expect(result.deletePassword.value).toBe('')
    expect(result.deleteError.value).toBe('')
    expect(options.logout).toHaveBeenCalledTimes(1)
    expect(options.pushHome).toHaveBeenCalledTimes(1)
  })

  it('does not log out a replacement session', async () => {
    let resolveDelete!: () => void
    const request = new Promise<void>((resolve) => { resolveDelete = resolve })
    const { result, options, setSessionGeneration } = setup(vi.fn(() => request))
    result.showDeleteModal.value = true
    result.deletePassword.value = 'secret'
    const pending = result.handleDeleteAccount()

    setSessionGeneration(1)
    resolveDelete()
    await pending

    expect(options.logout).not.toHaveBeenCalled()
  })
})
