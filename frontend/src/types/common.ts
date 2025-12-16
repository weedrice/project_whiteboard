// API 공통 응답 타입
export interface ApiResponse<T> {
    success: boolean
    data: T
    error?: {
        code: string
        message: string
    }
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
    sortOrder?: number
}
