import { describe, expect, it } from 'vitest'
import {
  buildSignupPayload,
  createSignupFieldState,
  createSignupForm,
  createSignupTouchedState,
  hasSignupFieldErrors,
  hydrateSignupFormFromQuery,
  isMaskedReregisterLoginBlocked,
  markAllSignupFieldsTouched,
} from '../signupRegistrationModel'

describe('signupRegistrationModel', () => {
  it('hydrates form values from the first query value', () => {
    const form = createSignupForm()

    const hydrated = hydrateSignupFormFromQuery(form, {
      email: ['user@example.com', 'other@example.com'],
      name: ['Display', 'Other'],
    })

    expect(hydrated).toEqual({
      email: 'user@example.com',
      name: 'Display',
    })
    expect(form.email).toBe('user@example.com')
    expect(form.displayName).toBe('Display')
  })

  it('builds a signup payload without passwordConfirm or browser-visible OAuth credential', () => {
    const form = {
      loginId: 'login_1',
      password: 'Password1!',
      passwordConfirm: 'Password1!',
      email: ' user@example.com ',
      displayName: ' Display ',
    }

    expect(buildSignupPayload(form, 'ticket-1')).toEqual({
      loginId: 'login_1',
      password: 'Password1!',
      email: 'user@example.com',
      displayName: 'Display',
      verificationTicket: 'ticket-1',
    })
  })

  it('resolves validation state helpers', () => {
    const touched = createSignupTouchedState()
    markAllSignupFieldsTouched(touched)
    expect(Object.values(touched).every(Boolean)).toBe(true)

    const fieldErrors = createSignupFieldState()
    expect(hasSignupFieldErrors(fieldErrors)).toBe(false)
    fieldErrors.email = 'invalid'
    expect(hasSignupFieldErrors(fieldErrors)).toBe(true)
    expect(isMaskedReregisterLoginBlocked(true, 'ma***ed')).toBe(true)
    expect(isMaskedReregisterLoginBlocked(true, 'restored-login')).toBe(false)
  })
})
