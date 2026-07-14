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
  const options = {
    deleteAccount,
    logout: vi.fn().mockResolvedValue(undefined),
    pushHome: vi.fn().mockResolvedValue(undefined),
    t: (key: string) => `t:${key}`,
  }
  return { result: useAccountDeletion(options), options }
}

describe('useAccountDeletion', () => {
  it('requires a password before calling the API', async () => {
    const { result, options } = setup()
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
    await result.handleDeleteAccount()
    expect(result.deleteError.value).toBe('wrong password')
    expect(mocks.loggerError).toHaveBeenCalled()
  })

  it('falls back to a translated generic error', async () => {
    mocks.extractValidationErrors.mockReturnValue(null)
    mocks.extractErrorMessage.mockReturnValue('')
    const { result } = setup(vi.fn().mockRejectedValue(new Error('bad')))
    result.deletePassword.value = 'bad'
    await result.handleDeleteAccount()
    expect(result.deleteError.value).toBe('t:common.errorOccurred')
  })
})
