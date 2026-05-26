// Query stale time constants
export const QUERY_STALE_TIME = {
    SHORT: 1000 * 60, // 1 minute
    MEDIUM: 1000 * 60 * 5, // 5 minutes
    LONG: 1000 * 60 * 60, // 1 hour
    DAY: 1000 * 60 * 60 * 24, // 24 hours
} as const

// Debounce delay constants (in milliseconds)
export const DEBOUNCE_DELAY = {
    SEARCH: 300,
    RESIZE: 150,
    SCROLL: 100,
    INPUT: 500,
} as const

// API constants
export const API = {
    TIMEOUT: 10000, // 10 seconds
    BASE_URL: import.meta.env.VITE_API_URL || '/api/v1',
} as const
