import logger from '@/utils/logger'

/**
 * 타입 안전한 localStorage 유틸리티
 */
export class Storage {
    /**
     * localStorage에서 값을 가져옵니다.
     * 
     * @param key 저장소 키
     * @param defaultValue 기본값 (키가 없거나 파싱 실패 시 반환)
     * @returns 저장된 값 또는 기본값
     * 
     * @example
     * ```typescript
     * const theme = Storage.get('theme', 'light')
     * const user = Storage.get<User>('user', null)
     * ```
     */
    static get<T>(key: string, defaultValue: T | null = null): T | null {
        try {
            const item = localStorage.getItem(key)
            if (item === null) {
                return defaultValue
            }
            return JSON.parse(item) as T
        } catch (error) {
            logger.error(`Failed to get item from localStorage: ${key}`, error)
            return defaultValue
        }
    }

    /**
     * localStorage에 값을 저장합니다.
     * 
     * @param key 저장소 키
     * @param value 저장할 값 (JSON 직렬화 가능한 값)
     * 
     * @example
     * ```typescript
     * Storage.set('theme', 'dark')
     * Storage.set('user', { id: 1, name: 'John' })
     * ```
     */
    static set<T>(key: string, value: T): void {
        try {
            localStorage.setItem(key, JSON.stringify(value))
        } catch (error) {
            logger.error(`Failed to set item to localStorage: ${key}`, error)
            // QuotaExceededError 처리
            if (error instanceof DOMException && error.name === 'QuotaExceededError') {
                logger.warn('localStorage quota exceeded. Consider clearing old data.')
            }
        }
    }

    /**
     * localStorage에서 값을 제거합니다.
     * 
     * @param key 저장소 키
     * 
     * @example
     * ```typescript
     * Storage.remove('theme')
     * ```
     */
    static remove(key: string): void {
        try {
            localStorage.removeItem(key)
        } catch (error) {
            logger.error(`Failed to remove item from localStorage: ${key}`, error)
        }
    }

    /**
     * localStorage의 모든 항목을 제거합니다.
     * 
     * @example
     * ```typescript
     * Storage.clear()
     * ```
     */
    static clear(): void {
        try {
            localStorage.clear()
        } catch (error) {
            logger.error('Failed to clear localStorage', error)
        }
    }

    /**
     * localStorage에 키가 존재하는지 확인합니다.
     * 
     * @param key 저장소 키
     * @returns 키 존재 여부
     * 
     * @example
     * ```typescript
     * if (Storage.has('theme')) {
     *   const theme = Storage.get('theme')
     * }
     * ```
     */
    static has(key: string): boolean {
        try {
            return localStorage.getItem(key) !== null
        } catch (error) {
            logger.error(`Failed to check item in localStorage: ${key}`, error)
            return false
        }
    }

    /**
     * localStorage의 모든 키를 가져옵니다.
     * 
     * @returns 키 배열
     * 
     * @example
     * ```typescript
     * const keys = Storage.keys()
     * // Process keys...
     * ```
     */
    static keys(): string[] {
        try {
            return Object.keys(localStorage)
        } catch (error) {
            logger.error('Failed to get keys from localStorage', error)
            return []
        }
    }

    /**
     * localStorage에서 문자열 값을 가져옵니다 (JSON 파싱 없이).
     * 토큰 등 JSON이 아닌 문자열 값에 사용합니다.
     * 
     * @param key 저장소 키
     * @param defaultValue 기본값 (키가 없을 경우 반환)
     * @returns 저장된 문자열 값 또는 기본값
     * 
     * @example
     * ```typescript
     * const token = Storage.getString('accessToken', '')
     * ```
     */
    static getString(key: string, defaultValue: string | null = null): string | null {
        try {
            const item = localStorage.getItem(key)
            return item !== null ? item : defaultValue
        } catch (error) {
            logger.error(`Failed to get string from localStorage: ${key}`, error)
            return defaultValue
        }
    }

    /**
     * localStorage에 문자열 값을 저장합니다 (JSON.stringify 없이).
     * 토큰 등 JSON이 아닌 문자열 값에 사용합니다.
     * 
     * @param key 저장소 키
     * @param value 저장할 문자열 값
     * 
     * @example
     * ```typescript
     * Storage.setString('accessToken', token)
     * ```
     */
    static setString(key: string, value: string): void {
        try {
            localStorage.setItem(key, value)
        } catch (error) {
            logger.error(`Failed to set string to localStorage: ${key}`, error)
            // QuotaExceededError 처리
            if (error instanceof DOMException && error.name === 'QuotaExceededError') {
                logger.warn('localStorage quota exceeded. Consider clearing old data.')
            }
        }
    }
}
