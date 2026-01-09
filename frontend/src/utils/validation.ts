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
 * Validates password strength (min 8 chars, at least one letter and one number)
 */
export const isValidPassword = (password: string): boolean => {
    // Minimum 8 characters, at least one letter and one number
    const passwordRegex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,}$/
    return passwordRegex.test(password)
}

/**
 * Validates ID format (alphanumeric, 4-20 chars)
 */
export const isValidLoginId = (loginId: string): boolean => {
    const idRegex = /^[a-zA-Z0-9]{4,20}$/
    return idRegex.test(loginId)
}
