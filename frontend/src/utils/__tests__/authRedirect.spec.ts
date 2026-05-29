import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearLoginRedirect,
  getStoredLoginRedirect,
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
})
