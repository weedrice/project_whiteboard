import logger from '@/utils/logger'

/**
 * Safe localStorage helpers.
 */
export class Storage {
    /**
     * Read and JSON-parse a localStorage value.
     */
    static get<T>(key: string, defaultValue: T | null = null): T | null {
        try {
            const item = localStorage.getItem(key)
            if (item === null) {
                return defaultValue
            }
            return JSON.parse(item) as T
        } catch (error: unknown) {
            logger.error(`Failed to get item from localStorage: ${key}`, error)
            return defaultValue
        }
    }

    /**
     * JSON-stringify and write a localStorage value.
     */
    static set<T>(key: string, value: T): void {
        try {
            localStorage.setItem(key, JSON.stringify(value))
        } catch (error: unknown) {
            logger.error(`Failed to set item to localStorage: ${key}`, error)
            if (error instanceof DOMException && error.name === 'QuotaExceededError') {
                logger.warn('localStorage quota exceeded. Consider clearing old data.')
            }
        }
    }

    /**
     * Remove a localStorage value.
     */
    static remove(key: string): void {
        try {
            localStorage.removeItem(key)
        } catch (error: unknown) {
            logger.error(`Failed to remove item from localStorage: ${key}`, error)
        }
    }

    /**
     * Clear all localStorage values for the current origin.
     */
    static clear(): void {
        try {
            localStorage.clear()
        } catch (error: unknown) {
            logger.error('Failed to clear localStorage', error)
        }
    }

    /**
     * Check whether a localStorage key exists.
     */
    static has(key: string): boolean {
        try {
            return localStorage.getItem(key) !== null
        } catch (error: unknown) {
            logger.error(`Failed to check item in localStorage: ${key}`, error)
            return false
        }
    }

    /**
     * Return all localStorage keys for the current origin.
     */
    static keys(): string[] {
        try {
            return Object.keys(localStorage)
        } catch (error: unknown) {
            logger.error('Failed to get keys from localStorage', error)
            return []
        }
    }

    /**
     * Read a raw string value from localStorage.
     */
    static getString(key: string, defaultValue: string | null = null): string | null {
        try {
            const item = localStorage.getItem(key)
            return item !== null ? item : defaultValue
        } catch (error: unknown) {
            logger.error(`Failed to get string from localStorage: ${key}`, error)
            return defaultValue
        }
    }

    /**
     * Write a raw string value to localStorage.
     */
    static setString(key: string, value: string): void {
        try {
            localStorage.setItem(key, value)
        } catch (error: unknown) {
            logger.error(`Failed to set string to localStorage: ${key}`, error)
            if (error instanceof DOMException && error.name === 'QuotaExceededError') {
                logger.warn('localStorage quota exceeded. Consider clearing old data.')
            }
        }
    }
}

/**
 * Safe sessionStorage helpers.
 */
export class SessionStorage {
    static getString(key: string, defaultValue: string | null = null): string | null {
        try {
            const item = sessionStorage.getItem(key)
            return item !== null ? item : defaultValue
        } catch (error: unknown) {
            logger.error(`Failed to get string from sessionStorage: ${key}`, error)
            return defaultValue
        }
    }

    static setString(key: string, value: string): void {
        try {
            sessionStorage.setItem(key, value)
        } catch (error: unknown) {
            logger.error(`Failed to set string to sessionStorage: ${key}`, error)
        }
    }

    static remove(key: string): void {
        try {
            sessionStorage.removeItem(key)
        } catch (error: unknown) {
            logger.error(`Failed to remove item from sessionStorage: ${key}`, error)
        }
    }
}
