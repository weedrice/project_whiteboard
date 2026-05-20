import { describe, expect, it } from 'vitest'
import { useAuthPasswordValidation } from '../useAuthPasswordValidation'

const messages = {
    required: 'auth.placeholders.password',
    invalid: 'auth.validation.passwordStrength',
    mismatch: 'auth.passwordMismatch',
}

describe('useAuthPasswordValidation', () => {
    it('validates a single password value for field-level checks', () => {
        const { validatePasswordValue } = useAuthPasswordValidation()

        expect(validatePasswordValue('', {
            messages,
        })).toBeNull()
        expect(validatePasswordValue('', {
            requirePassword: true,
            messages,
        })).toBe('auth.placeholders.password')
        expect(validatePasswordValue('weak', {
            messages,
        })).toBe('auth.validation.passwordStrength')
        expect(validatePasswordValue('Password1!', {
            messages,
        })).toBeNull()
    })

    it('validates a single password confirmation value for field-level checks', () => {
        const { validatePasswordConfirmValue } = useAuthPasswordValidation()

        expect(validatePasswordConfirmValue('Password1!', '', {
            messages,
        })).toBeNull()
        expect(validatePasswordConfirmValue('Password1!', '', {
            requireConfirm: true,
            messages,
        })).toBe('auth.passwordMismatch')
        expect(validatePasswordConfirmValue('Password1!', 'Password2!', {
            messages,
        })).toBe('auth.passwordMismatch')
        expect(validatePasswordConfirmValue('Password1!', 'Password1!', {
            messages,
        })).toBeNull()
    })

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
