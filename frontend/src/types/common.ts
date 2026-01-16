// API 에러 응답 타입
export interface ErrorResponse {
    code: string
    message: string
    details?: ValidationErrors | any // Validation 에러는 Map<string, string[]> 형태
}

// Validation 에러 타입 (필드명 -> 에러 메시지 배열)
export type ValidationErrors = Record<string, string[]>

// API 공통 응답 타입
export interface ApiResponse<T> {
    success: boolean
    data: T
    error?: ErrorResponse
}

// 페이지네이션 응답
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
