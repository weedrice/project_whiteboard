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

export interface PasswordValueValidationOptions {
    requirePassword?: boolean
    messages: Pick<PasswordValidationMessages, 'required' | 'invalid'>
}

export interface PasswordConfirmValidationOptions {
    requireConfirm?: boolean
    messages: Pick<PasswordValidationMessages, 'mismatch'>
}

export function useAuthPasswordValidation() {
    const validatePasswordValue = (
        password: string,
        options: PasswordValueValidationOptions
    ): string | null => {
        if (!password) {
            return options.requirePassword
                ? options.messages.required ?? options.messages.invalid
                : null
        }

        if (!isValidPassword(password)) {
            return options.messages.invalid
        }

        return null
    }

    const validatePasswordConfirmValue = (
        password: string,
        confirmPassword: string,
        options: PasswordConfirmValidationOptions
    ): string | null => {
        if (!confirmPassword) {
            return options.requireConfirm ? options.messages.mismatch : null
        }

        if (password !== confirmPassword) {
            return options.messages.mismatch
        }

        return null
    }

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
        validatePasswordValue,
        validatePasswordConfirmValue,
        validatePasswordPair,
    }
}
