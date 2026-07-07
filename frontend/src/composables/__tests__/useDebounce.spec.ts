import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useDebounce, useDebounceFn } from '../useDebounce'

describe('useDebounce', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it('does not update the debounced value after the scope is disposed', () => {
        vi.useFakeTimers()
        const source = ref('initial')
        const scope = effectScope()
        let debounced!: ReturnType<typeof useDebounce<string>>

        scope.run(() => {
            debounced = useDebounce(source, 100)
        })

        source.value = 'next'
        scope.stop()
        vi.advanceTimersByTime(100)

        expect(debounced.value).toBe('initial')
    })

    it('applies only the latest value after the delay', async () => {
        vi.useFakeTimers()
        const source = ref('initial')
        const debounced = useDebounce(source, 100)

        source.value = 'first'
        await nextTick()
        vi.advanceTimersByTime(50)
        source.value = 'second'
        await nextTick()

        vi.advanceTimersByTime(99)
        expect(debounced.value).toBe('initial')

        vi.advanceTimersByTime(1)
        expect(debounced.value).toBe('second')
    })
})

describe('useDebounceFn', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it('does not run a pending callback after the scope is disposed', () => {
        vi.useFakeTimers()
        const callback = vi.fn()
        const scope = effectScope()

        scope.run(() => {
            const debounced = useDebounceFn(callback, 100)
            debounced('pending')
        })

        scope.stop()
        vi.advanceTimersByTime(100)

        expect(callback).not.toHaveBeenCalled()
    })

    it('exposes a cancel helper for pending callbacks', () => {
        vi.useFakeTimers()
        const callback = vi.fn()
        const debounced = useDebounceFn(callback, 100)

        debounced('pending')
        debounced.cancel()
        vi.advanceTimersByTime(100)

        expect(callback).not.toHaveBeenCalled()
    })
})
