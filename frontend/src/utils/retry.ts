/**
 * 재시도 유틸리티 함수
 */

export interface RetryOptions {
    maxRetries?: number
    retryDelay?: number
    retryCondition?: (error: any) => boolean
    onRetry?: (attempt: number, error: any) => void
}

/**
 * 재시도 가능한 네트워크 오류인지 확인
 */
export function isRetryableError(error: any): boolean {
    // 네트워크 오류 (요청이 전송되지 않음)
    if (!error.response) {
        return true
    }

    const status = error.response?.status

    // 5xx 서버 오류는 재시도 가능
    if (status >= 500 && status < 600) {
        return true
    }

    // 408 Request Timeout
    if (status === 408) {
        return true
    }

    // 429 Too Many Requests (Rate Limiting)
    if (status === 429) {
        return true
    }

    return false
}

/**
 * 지수 백오프 지연 시간 계산
 * @param attempt 시도 횟수 (1부터 시작)
 * @param baseDelay 기본 지연 시간 (ms)
 * @returns 지연 시간 (ms)
 */
export function calculateBackoffDelay(attempt: number, baseDelay: number = 1000): number {
    return Math.min(baseDelay * Math.pow(2, attempt - 1), 10000) // 최대 10초
}

/**
 * 비동기 함수를 재시도 로직과 함께 실행
 * @param fn 실행할 비동기 함수
 * @param options 재시도 옵션
 * @returns 함수 실행 결과
 */
export async function withRetry<T>(
    fn: () => Promise<T>,
    options: RetryOptions = {}
): Promise<T> {
    const {
        maxRetries = 3,
        retryDelay = 1000,
        retryCondition = isRetryableError,
        onRetry
    } = options

    let lastError: any

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return await fn()
        } catch (error) {
            lastError = error

            // 재시도 조건 확인
            if (!retryCondition(error)) {
                throw error
            }

            // 마지막 시도인 경우 에러 throw
            if (attempt >= maxRetries) {
                throw error
            }

            // 재시도 콜백 호출
            if (onRetry) {
                onRetry(attempt, error)
            }

            // 지수 백오프 지연
            const delay = calculateBackoffDelay(attempt, retryDelay)
            await new Promise(resolve => setTimeout(resolve, delay))
        }
    }

    throw lastError
}
