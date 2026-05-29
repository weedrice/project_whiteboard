import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildDeletedAccountSignupPath,
  clearLoginRedirect,
  DELETED_ACCOUNT_MESSAGE_KEY,
  getStoredLoginRedirect,
  handleDeletedAccountRedirect,
  isSafeRedirect,
  LOGIN_REDIRECT_KEY,
  resolveLoginRedirect,
  saveLoginRedirect
} from '../authRedirect'

describe('authRedirect', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('accepts only same-origin absolute-path redirects', () => {
    expect(isSafeRedirect('/board/free')).toBe(true)
    expect(isSafeRedirect('//evil.example')).toBe(false)
    expect(isSafeRedirect('https://evil.example')).toBe(false)
    expect(isSafeRedirect(null)).toBe(false)
  })

  it('stores and consumes the existing loginRedirect raw string key', () => {
    saveLoginRedirect('/board/free')

    expect(sessionStorage.getItem(LOGIN_REDIRECT_KEY)).toBe('/board/free')
    expect(getStoredLoginRedirect()).toBe('/board/free')

    clearLoginRedirect()

    expect(sessionStorage.getItem(LOGIN_REDIRECT_KEY)).toBeNull()
  })

  it('prefers a safe query redirect and falls back to safe storage', () => {
    saveLoginRedirect('/stored')

    expect(resolveLoginRedirect('/query')).toBe('/query')
    expect(resolveLoginRedirect('//evil.example')).toBe('/stored')
  })

  it('builds the deleted-account signup redirect with a trimmed encoded email', () => {
    expect(buildDeletedAccountSignupPath(' deleted+user@example.com ')).toBe('/signup?email=deleted%2Buser%40example.com')
  })

  it('handles deleted-account API errors with the shared toast and redirect', () => {
    const addToast = vi.fn()
    const push = vi.fn()
    const error = {
      response: {
        data: {
          error: {
            code: 'A009',
            message: 'deleted',
          },
        },
      },
    }

    const handled = handleDeletedAccountRedirect(error, {
      email: 'deleted+user@example.com',
      t: (key) => key,
      addToast,
      push,
    })

    expect(handled).toBe(true)
    expect(addToast).toHaveBeenCalledWith(DELETED_ACCOUNT_MESSAGE_KEY, 'info')
    expect(push).toHaveBeenCalledWith('/signup?email=deleted%2Buser%40example.com')
  })

  it('does not handle unrelated auth errors', () => {
    const addToast = vi.fn()
    const push = vi.fn()
    const error = {
      response: {
        data: {
          error: {
            code: 'A001',
            message: 'failed',
          },
        },
      },
    }

    const handled = handleDeletedAccountRedirect(error, {
      email: 'user@example.com',
      t: (key) => key,
      addToast,
      push,
    })

    expect(handled).toBe(false)
    expect(addToast).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
  })
})
