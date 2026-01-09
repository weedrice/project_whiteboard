// Query stale time constants
export const QUERY_STALE_TIME = {
    SHORT: 1000 * 60, // 1 minute
    MEDIUM: 1000 * 60 * 5, // 5 minutes
    LONG: 1000 * 60 * 60, // 1 hour
    DAY: 1000 * 60 * 60 * 24, // 24 hours
} as const

// Pagination constants
export const PAGINATION = {
    DEFAULT_PAGE_SIZE: 20,
    MAX_PAGE_SIZE: 100,
    DEFAULT_PAGE: 0,
} as const

// File upload constants
export const FILE_UPLOAD = {
    MAX_SIZE: 10 * 1024 * 1024, // 10MB
    ALLOWED_TYPES: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'] as const,
    MAX_DIMENSION: 2048, // Maximum width or height in pixels
} as const

// Debounce delay constants (in milliseconds)
export const DEBOUNCE_DELAY = {
    SEARCH: 300,
    RESIZE: 150,
    SCROLL: 100,
    INPUT: 500,
} as const

// Animation duration constants (in milliseconds)
export const ANIMATION_DURATION = {
    SHORT: 200,
    MEDIUM: 300,
    LONG: 500,
} as const

// Image optimization constants
export const IMAGE_OPTIMIZATION = {
    PROFILE_SIZE: 150,
    BOARD_ICON_SIZE: 80,
    POST_IMAGE_WIDTH: 800,
    QUALITY: 85,
} as const
