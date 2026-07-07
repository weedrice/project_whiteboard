import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { effectScope, nextTick, ref, type Ref } from 'vue'
import { useThrottle, useThrottleFn } from '../useThrottle'

describe('useThrottle', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(1000)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('cleans up pending value updates when the effect scope is disposed', () => {
        const scope = effectScope()
        const source = ref('initial')
        let throttled: Ref<string>

        scope.run(() => {
            throttled = useThrottle(source, 100)
            source.value = 'next'
        })

        scope.stop()
        vi.advanceTimersByTime(100)

        expect(throttled!.value).toBe('initial')
    })

    it('applies the trailing ref value at the throttle boundary', async () => {
        const source = ref('initial')
        const scope = effectScope()
        let throttled: Ref<string>

        scope.run(() => {
            throttled = useThrottle(source, 100)
        })

        source.value = 'first'
        await nextTick()
        expect(throttled!.value).toBe('initial')

        vi.advanceTimersByTime(99)
        expect(throttled!.value).toBe('initial')

        vi.advanceTimersByTime(1)
        expect(throttled!.value).toBe('first')

        source.value = 'second'
        await nextTick()
        expect(throttled!.value).toBe('first')

        vi.advanceTimersByTime(100)
        expect(throttled!.value).toBe('second')
        scope.stop()
    })
})

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
