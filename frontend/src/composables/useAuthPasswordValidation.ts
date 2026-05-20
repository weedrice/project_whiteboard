import { isValidPassword } from '@/utils/validation'

export interface PasswordValidationMessages {
    required?: string
    invalid: string
    mismatch: string
}

export interface PasswordPairValidationOptions {
    requirePassword?: boolean
    requireConfirm?: boolean
    messages: PasswordValidationMessages
}

export function useAuthPasswordValidation() {
    const validatePasswordPair = (
        password: string,
        confirmPassword: string,
        options: PasswordPairValidationOptions
    ): string | null => {
        if (options.requirePassword && !password) {
            return options.messages.required ?? options.messages.invalid
        }

        if (!isValidPassword(password)) {
            return options.messages.invalid
        }

        if (options.requireConfirm && !confirmPassword) {
            return options.messages.mismatch
        }

        if (password !== confirmPassword) {
            return options.messages.mismatch
        }

        return null
    }

    return {
        validatePasswordPair,
    }
}
