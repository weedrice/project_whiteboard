import { describe, expect, it } from 'vitest'
import { useAuthPasswordValidation } from '../useAuthPasswordValidation'

const messages = {
    required: 'auth.placeholders.password',
    invalid: 'auth.validation.passwordStrength',
    mismatch: 'auth.passwordMismatch',
}

describe('useAuthPasswordValidation', () => {
    it('returns the required message when password is required and empty', () => {
        const { validatePasswordPair } = useAuthPasswordValidation()

        expect(validatePasswordPair('', '', {
            requirePassword: true,
            messages,
        })).toBe('auth.placeholders.password')
    })

    it('returns the invalid message for weak passwords', () => {
        const { validatePasswordPair } = useAuthPasswordValidation()

        expect(validatePasswordPair('weak', 'weak', {
            messages,
        })).toBe('auth.validation.passwordStrength')
    })

    it('returns the mismatch message when confirmation is different', () => {
        const { validatePasswordPair } = useAuthPasswordValidation()

        expect(validatePasswordPair('Password1!', 'Password2!', {
            messages,
        })).toBe('auth.passwordMismatch')
    })

    it('returns null for a valid matching password pair', () => {
        const { validatePasswordPair } = useAuthPasswordValidation()

        expect(validatePasswordPair('Password1!', 'Password1!', {
            messages,
        })).toBeNull()
    })
})
