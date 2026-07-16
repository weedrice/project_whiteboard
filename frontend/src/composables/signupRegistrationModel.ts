import type { LocationQuery } from 'vue-router'
import { getSingleQueryValue } from '@/utils/routeQueryValue'

export type SignupForm = {
  loginId: string
  password: string
  passwordConfirm: string
  email: string
  displayName: string
}

export type SignupFieldState = Record<keyof SignupForm, string>
export type SignupTouchedState = Record<keyof SignupForm, boolean>

export type SignupPayload = Omit<SignupForm, 'passwordConfirm'> & {
  verificationTicket: string
  oauthRegistrationTicket: string | null
}

export function createSignupForm(): SignupForm {
  return {
    loginId: '',
    password: '',
    passwordConfirm: '',
    email: '',
    displayName: ''
  }
}

export function createSignupFieldState(): SignupFieldState {
  return {
    loginId: '',
    password: '',
    passwordConfirm: '',
    email: '',
    displayName: ''
  }
}

export function createSignupTouchedState(): SignupTouchedState {
  return {
    loginId: false,
    password: false,
    passwordConfirm: false,
    email: false,
    displayName: false
  }
}

export function markAllSignupFieldsTouched(touched: SignupTouchedState) {
  Object.keys(touched).forEach((field) => {
    touched[field as keyof SignupTouchedState] = true
  })
}

export function hasSignupFieldErrors(fieldErrors: SignupFieldState): boolean {
  return Object.values(fieldErrors).some(Boolean)
}

export function hydrateSignupFormFromQuery(form: SignupForm, query: LocationQuery) {
  const email = getSingleQueryValue(query.email)
  const name = getSingleQueryValue(query.name)

  if (email) {
    form.email = email
  }
  if (name) {
    form.displayName = name
  }

  return {
    email,
    name,
  }
}

export function hydrateSignupFormFromOAuthTicket(
  form: SignupForm,
  ticket: { email?: string | null; name?: string | null },
) {
  if (ticket.email) {
    form.email = ticket.email
  }
  if (ticket.name) {
    form.displayName = ticket.name
  }
}

export function buildSignupPayload(
  form: SignupForm,
  verificationTicket: string,
  query: LocationQuery,
): SignupPayload {
  const { passwordConfirm, ...formData } = form
  void passwordConfirm

  return {
    ...formData,
    email: form.email.trim(),
    displayName: form.displayName.trim(),
    verificationTicket,
    oauthRegistrationTicket: getSingleQueryValue(query.oauthRegistrationTicket)
  }
}

export function isMaskedReregisterLoginBlocked(isReregister: boolean, loginId: string): boolean {
  return isReregister && loginId.includes('*')
}
