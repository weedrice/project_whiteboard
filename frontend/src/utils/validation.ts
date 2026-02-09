/**
 * Checks if a value is empty (null, undefined, empty string, or empty array)
 */
export const isEmpty = (value: unknown): boolean => {
    if (value === null || value === undefined) return true
    if (typeof value === 'string' && value.trim() === '') return true
    if (Array.isArray(value) && value.length === 0) return true
    return false
}

/**
 * Validates email format
 */
export const isValidEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailRegex.test(email)
}

/**
 * Validates password strength
 * - Minimum 8 characters, maximum 20 characters
 * - Must contain at least 3 of the following: uppercase, lowercase, digit, special character
 */
export const isValidPassword = (password: string): boolean => {
    if (password.length < 8 || password.length > 20) return false

    let typeCount = 0
    if (/[A-Z]/.test(password)) typeCount++
    if (/[a-z]/.test(password)) typeCount++
    if (/[0-9]/.test(password)) typeCount++
    if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) typeCount++

    return typeCount >= 3
}

/**
 * Validates ID format (alphanumeric + underscore, 4-30 chars)
 */
export const isValidLoginId = (loginId: string): boolean => {
    const idRegex = /^[a-zA-Z0-9_]{4,30}$/
    return idRegex.test(loginId)
}

/**
 * Validates display name length (2-50 chars)
 */
export const isValidDisplayName = (displayName: string): boolean => {
    const trimmed = displayName.trim()
    return trimmed.length >= 2 && trimmed.length <= 50
}
