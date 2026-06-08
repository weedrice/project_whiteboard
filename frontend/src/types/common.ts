// API error response types
export interface ErrorResponse {
    code: string
    message: string
    details?: ValidationErrors | Record<string, unknown>
}

// Validation errors are keyed by field name.
export type ValidationErrors = Record<string, string[]>

// Shared API response wrapper
export interface ApiResponse<T> {
    success: boolean
    data: T
    error?: ErrorResponse
}

// Paginated API response
export interface PageResponse<T> {
    content: T[]
    totalElements: number
    totalPages: number
    size: number
    number: number
    first: boolean
    last: boolean
    empty: boolean
}

export interface Code {
    code: string
    name: string
    value?: string
    sortOrder?: number
}
