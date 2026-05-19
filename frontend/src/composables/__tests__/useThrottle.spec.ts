import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { effectScope } from 'vue'
import { useThrottleFn } from '../useThrottle'

describe('useThrottleFn', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(1000)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('keeps the returned value callable and runs the trailing call after the delay', () => {
        const calls: string[] = []
        const throttled = useThrottleFn((value: string) => {
            calls.push(value)
        }, 100)

        throttled('first')
        vi.advanceTimersByTime(50)
        throttled('second')

        expect(calls).toEqual(['first'])

        vi.advanceTimersByTime(49)
        expect(calls).toEqual(['first'])

        vi.advanceTimersByTime(1)
        expect(calls).toEqual(['first', 'second'])
    })

    it('cancels a pending trailing call', () => {
        const calls: string[] = []
        const throttled = useThrottleFn((value: string) => {
            calls.push(value)
        }, 100)

        throttled('first')
        vi.advanceTimersByTime(50)
        throttled('second')
        throttled.cancel()
        vi.advanceTimersByTime(100)

        expect(calls).toEqual(['first'])
    })

    it('cleans up pending work when the effect scope is disposed', () => {
        const calls: string[] = []
        const scope = effectScope()
        const throttled = scope.run(() => useThrottleFn((value: string) => {
            calls.push(value)
        }, 100))

        expect(throttled).toBeDefined()

        throttled?.('first')
        vi.advanceTimersByTime(50)
        throttled?.('second')
        scope.stop()
        vi.advanceTimersByTime(100)

        expect(calls).toEqual(['first'])
    })
})
