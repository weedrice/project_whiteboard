import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildDeletedAccountSignupPath,
  clearLoginRedirect,
  consumeLoginRedirect,
  DELETED_ACCOUNT_MESSAGE_KEY,
  getStoredLoginRedirect,
  handleDeletedAccountRedirect,
  isSafeRedirect,
  LOGIN_REDIRECT_KEY,
  resolveLoginRedirect,
  saveLoginRedirect,
  shouldExitProtectedRouteAfterSessionLoss,
} from '../authRedirect'

describe('authRedirect', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('accepts only same-origin absolute-path redirects', () => {
    expect(isSafeRedirect('/board/free')).toBe(true)
    expect(isSafeRedirect('//evil.example')).toBe(false)
    expect(isSafeRedirect('/%252fevil.example')).toBe(false)
    expect(isSafeRedirect('/%255cevil.example')).toBe(false)
    expect(isSafeRedirect('https://evil.example')).toBe(false)
    expect(isSafeRedirect(null)).toBe(false)
  })

  it('stores a TTL-bound nonce and consumes the redirect once', () => {
    const nonce = saveLoginRedirect('/board/free', 1000)

    expect(nonce).toBeTruthy()
    expect(JSON.parse(sessionStorage.getItem(LOGIN_REDIRECT_KEY) ?? '{}')).toMatchObject({
      path: '/board/free',
      expiresAt: 601000,
      nonce,
    })
    expect(getStoredLoginRedirect(1001)).toBe('/board/free')
    expect(consumeLoginRedirect(1001)).toBe('/board/free')
    expect(consumeLoginRedirect(1001)).toBeNull()

    saveLoginRedirect('/expired', 1000)
    expect(getStoredLoginRedirect(601001)).toBeNull()

    clearLoginRedirect()

    expect(sessionStorage.getItem(LOGIN_REDIRECT_KEY)).toBeNull()
  })

  it('prefers a safe query redirect and falls back to safe storage', () => {
    saveLoginRedirect('/stored')

    expect(resolveLoginRedirect('/query')).toBe('/query')
    expect(resolveLoginRedirect('//evil.example')).toBe('/stored')
    expect(resolveLoginRedirect('//evil.example')).toBe('/')
  })

  it('exits a protected route only when an authenticated session is lost', () => {
    expect(shouldExitProtectedRouteAfterSessionLoss(false, true, true)).toBe(true)
    expect(shouldExitProtectedRouteAfterSessionLoss(false, undefined, true)).toBe(false)
    expect(shouldExitProtectedRouteAfterSessionLoss(false, true, false)).toBe(false)
    expect(shouldExitProtectedRouteAfterSessionLoss(true, true, true)).toBe(false)
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

  it('does not throw or handle non-object errors', () => {
    const addToast = vi.fn()
    const push = vi.fn()

    expect(handleDeletedAccountRedirect(null, {
      email: 'user@example.com',
      t: (key) => key,
      addToast,
      push,
    })).toBe(false)

    expect(handleDeletedAccountRedirect(undefined, {
      email: 'user@example.com',
      t: (key) => key,
      addToast,
      push,
    })).toBe(false)
    expect(addToast).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
  })
})
