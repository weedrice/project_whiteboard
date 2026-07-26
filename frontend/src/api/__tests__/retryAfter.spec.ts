import { AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import {
    MAX_AUTO_RETRY_AFTER_MS,
    getRetryAfterMs,
    parseRetryAfterMs,
    shouldRetryAfterDelay,
} from '@/api/retryAfter'

const NOW = Date.parse('2026-07-25T00:00:00Z')

describe('parseRetryAfterMs', () => {
    it('초 단위 정수를 밀리초로 바꾼다', () => {
        expect(parseRetryAfterMs('3', NOW)).toBe(3000)
        expect(parseRetryAfterMs(' 12 ', NOW)).toBe(12000)
    })

    it('0초를 그대로 유지한다', () => {
        expect(parseRetryAfterMs('0', NOW)).toBe(0)
    })

    it('상한을 넘는 값도 그대로 돌려준다', () => {
        // 상한 판단은 shouldRetryAfterDelay가 맡는다.
        expect(parseRetryAfterMs('86400', NOW)).toBe(86_400_000)
    })

    it('RFC 9110의 세 가지 HTTP-date 형식을 모두 받는다', () => {
        expect(parseRetryAfterMs('Sat, 25 Jul 2026 00:00:05 GMT', NOW)).toBe(5000)
        expect(parseRetryAfterMs('Saturday, 25-Jul-26 00:00:05 GMT', NOW)).toBe(5000)
        expect(parseRetryAfterMs('Sat Jul 25 00:00:05 2026', NOW)).toBe(5000)
    })

    it('이미 지난 HTTP-date는 0으로 내린다', () => {
        expect(parseRetryAfterMs('Fri, 24 Jul 2026 23:59:50 GMT', NOW)).toBe(0)
    })

    it('HTTP-date 형식이 아닌 문자열은 Date.parse에 넘기지 않는다', () => {
        // Date.parse('March 5')는 성공하므로 형식 검사가 없으면 0ms 즉시 재시도로 샌다.
        expect(parseRetryAfterMs('March 5', NOW)).toBeNull()
        expect(parseRetryAfterMs('soon', NOW)).toBeNull()
        expect(parseRetryAfterMs('Sat, 25 Jul 2026 00:00:05', NOW)).toBeNull()
    })

    it('해석할 수 없는 값에는 null을 돌려준다', () => {
        expect(parseRetryAfterMs(undefined, NOW)).toBeNull()
        expect(parseRetryAfterMs('', NOW)).toBeNull()
        expect(parseRetryAfterMs('   ', NOW)).toBeNull()
        expect(parseRetryAfterMs('-5', NOW)).toBeNull()
        expect(parseRetryAfterMs(3 as unknown, NOW)).toBeNull()
    })
})

describe('getRetryAfterMs', () => {
    it('실제 AxiosHeaders에서 대소문자 구분 없이 읽는다', () => {
        const error = {
            response: { status: 429, headers: new AxiosHeaders({ 'Retry-After': '4' }) },
        }
        expect(getRetryAfterMs(error, NOW)).toBe(4000)
    })

    it('평범한 객체 헤더도 대소문자 구분 없이 읽는다', () => {
        expect(getRetryAfterMs({ response: { status: 429, headers: { 'retry-after': '4' } } }, NOW)).toBe(4000)
        expect(getRetryAfterMs({ response: { status: 429, headers: { 'Retry-After': '4' } } }, NOW)).toBe(4000)
    })

    it('헤더나 응답이 없으면 null을 돌려준다', () => {
        expect(getRetryAfterMs(undefined, NOW)).toBeNull()
        expect(getRetryAfterMs({}, NOW)).toBeNull()
        expect(getRetryAfterMs({ response: { status: 429 } }, NOW)).toBeNull()
        expect(getRetryAfterMs({ response: { status: 429, headers: {} } }, NOW)).toBeNull()
    })
})

describe('shouldRetryAfterDelay', () => {
    it('상한 이하만 자동 재시도를 허용한다', () => {
        expect(shouldRetryAfterDelay(0)).toBe(true)
        expect(shouldRetryAfterDelay(MAX_AUTO_RETRY_AFTER_MS)).toBe(true)
        expect(shouldRetryAfterDelay(MAX_AUTO_RETRY_AFTER_MS + 1)).toBe(false)
    })

    it('지시가 없으면 자동 재시도 대상이 아니다', () => {
        expect(shouldRetryAfterDelay(null)).toBe(false)
    })
})
