import type { AxiosError } from 'axios'

/** 백엔드 `RateLimitHeaderWriter`가 기록하는 헤더 이름. */
export const RATE_LIMIT_HEADERS = {
    LIMIT: 'ratelimit-limit',
    REMAINING: 'ratelimit-remaining',
    RESET: 'ratelimit-reset',
    RETRY_AFTER: 'retry-after',
} as const

/**
 * 자동 재시도를 위해 기다려 줄 상한.
 * 이보다 오래 기다리라는 지시는 화면을 그만큼 멈춰 세우는 편이 손해이므로,
 * 재시도하지 않고 즉시 오류를 노출한다.
 */
export const MAX_AUTO_RETRY_AFTER_MS = 10_000

/**
 * RFC 9110 §5.6.7이 정의하는 세 가지 HTTP-date 형식.
 * `Date.parse`는 'March 5' 같은 값도 받아들이므로 형식을 먼저 좁힌다.
 */
const HTTP_DATE_PATTERN = new RegExp(
    '^(?:'
    // IMF-fixdate: Sun, 06 Nov 1994 08:49:37 GMT
    + '[A-Za-z]{3}, \\d{2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT'
    // RFC 850: Sunday, 06-Nov-94 08:49:37 GMT
    + '|[A-Za-z]{6,9}, \\d{2}-[A-Za-z]{3}-\\d{2} \\d{2}:\\d{2}:\\d{2} GMT'
    // asctime: Sun Nov  6 08:49:37 1994
    + '|[A-Za-z]{3} [A-Za-z]{3} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\d{4}'
    + ')$',
)

/**
 * RFC 9110 `Retry-After` 값을 밀리초로 해석한다.
 * delta-seconds(음수 아닌 정수)와 HTTP-date 두 형식만 받으며,
 * 형식에 맞지 않으면 null을 돌려줘 호출부가 자체 백오프로 떨어지게 한다.
 *
 * @param value 헤더 원본 문자열
 * @param now HTTP-date 형식을 상대 시간으로 바꿀 기준 시각
 */
export function parseRetryAfterMs(value: unknown, now: number = Date.now()): number | null {
    if (typeof value !== 'string') return null

    const trimmed = value.trim()
    if (!trimmed) return null

    if (/^\d+$/.test(trimmed)) {
        const seconds = Number(trimmed)
        if (!Number.isFinite(seconds)) return null
        return Math.max(seconds * 1000, 0)
    }

    if (!HTTP_DATE_PATTERN.test(trimmed)) return null

    const dateMs = Date.parse(trimmed)
    if (Number.isNaN(dateMs)) return null
    return Math.max(dateMs - now, 0)
}

/**
 * 429 응답에서 서버가 지시한 대기 시간을 얻는다.
 * 헤더가 없거나 해석할 수 없으면 null을 돌려주고, 호출부가 자체 백오프로 떨어진다.
 * 반환값에는 상한을 적용하지 않는다. 상한 판단은 {@link shouldRetryAfterDelay}가 맡는다.
 */
export function getRetryAfterMs(error: unknown, now: number = Date.now()): number | null {
    const response = (error as AxiosError | undefined)?.response
    if (!response) return null

    return parseRetryAfterMs(readHeader(response.headers, RATE_LIMIT_HEADERS.RETRY_AFTER), now)
}

/**
 * 서버가 지시한 대기 시간이 자동 재시도로 감당할 만한지 판단한다.
 * 상한을 넘으면 화면을 멈춰 두는 대신 즉시 오류를 노출하는 편이 낫다.
 */
export function shouldRetryAfterDelay(delayMs: number | null): boolean {
    return delayMs !== null && delayMs <= MAX_AUTO_RETRY_AFTER_MS
}

/** axios `AxiosHeaders`와 평범한 객체를 모두 대소문자 구분 없이 읽는다. */
function readHeader(headers: unknown, name: string): unknown {
    if (!headers || typeof headers !== 'object') return undefined

    const source = headers as Record<string, unknown> & { get?: (headerName: string) => unknown }
    if (typeof source.get === 'function') {
        return source.get(name)
    }

    const lowerName = name.toLowerCase()
    const matchedKey = Object.keys(source).find((key) => key.toLowerCase() === lowerName)
    return matchedKey ? source[matchedKey] : undefined
}
