import { AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { queryClient } from '@/queryClient'

type RetryFn = (failureCount: number, error: unknown) => boolean
type RetryDelayFn = (attemptIndex: number, error: unknown) => number

const queryDefaults = () => {
    const options = queryClient.getDefaultOptions().queries as {
        retry: RetryFn
        retryDelay: RetryDelayFn
    }
    return { retry: options.retry, retryDelay: options.retryDelay }
}

const rateLimited = (retryAfter?: string) => ({
    response: {
        status: 429,
        headers: retryAfter === undefined ? new AxiosHeaders() : new AxiosHeaders({ 'Retry-After': retryAfter }),
    },
})

const serverError = { response: { status: 503, headers: new AxiosHeaders() } }

describe('queryClient 429 재시도 정책', () => {
    it('Retry-After가 상한 이하면 그 값만큼 기다렸다 한 번만 재시도한다', () => {
        const { retry, retryDelay } = queryDefaults()

        expect(retry(0, rateLimited('4'))).toBe(true)
        expect(retryDelay(0, rateLimited('4'))).toBe(4000)
        expect(retry(1, rateLimited('4'))).toBe(false)
    })

    it('Retry-After가 상한을 넘으면 재시도하지 않는다', () => {
        const { retry } = queryDefaults()

        expect(retry(0, rateLimited('600'))).toBe(false)
    })

    it('Retry-After가 없으면 기존 지수 백오프로 최대 3회 재시도한다', () => {
        const { retry, retryDelay } = queryDefaults()

        expect(retry(0, rateLimited())).toBe(true)
        expect(retry(2, rateLimited())).toBe(true)
        expect(retry(3, rateLimited())).toBe(false)
        expect(retryDelay(0, rateLimited())).toBe(1000)
        expect(retryDelay(1, rateLimited())).toBe(2000)
        expect(retryDelay(2, rateLimited())).toBe(4000)
    })

    it('5xx는 Retry-After와 무관하게 기존 정책을 유지한다', () => {
        const { retry, retryDelay } = queryDefaults()

        expect(retry(0, serverError)).toBe(true)
        expect(retry(2, serverError)).toBe(false)
        expect(retryDelay(3, serverError)).toBe(8000)
    })

    it('지수 백오프 상한은 30초를 넘지 않는다', () => {
        const { retryDelay } = queryDefaults()

        expect(retryDelay(20, serverError)).toBe(30000)
    })
})
