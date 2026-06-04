import { onScopeDispose, ref, watch, type Ref } from 'vue'

export type ThrottledFunction<T extends (...args: never[]) => unknown> = ((...args: Parameters<T>) => void) & {
    cancel: () => void
}

/**
 * Returns a throttled ref that updates at most once per delay.
 *
 * @param value source ref to throttle
 * @param delay throttle delay in milliseconds
 * @returns throttled ref value
 *
 * @example
 * ```typescript
 * const scrollY = ref(0)
 * const throttledScrollY = useThrottle(scrollY, 100)
 *
 * watch(throttledScrollY, (value) => {
 *   // Runs at most every 100ms.
 *   updateScrollPosition(value)
 * })
 * ```
 */
export function useThrottle<T>(value: Ref<T>, delay: number = 100): Ref<T> {
    const throttled = ref(value.value) as Ref<T>
    let lastUpdate = 0
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    watch(value, (newValue) => {
        const now = Date.now()
        const timeSinceLastUpdate = now - lastUpdate

        if (timeSinceLastUpdate >= delay) {
            throttled.value = newValue
            lastUpdate = now
        } else {
            // Schedule the trailing value update.
            if (timeoutId) {
                clearTimeout(timeoutId)
            }

            timeoutId = setTimeout(() => {
                throttled.value = newValue
                lastUpdate = Date.now()
                timeoutId = null
            }, delay - timeSinceLastUpdate)
        }
    }, { immediate: true })

    onScopeDispose(() => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
    })

    return throttled
}

/**
 * Returns a throttled function with a cancel helper.
 *
 * @param fn function to throttle
 * @param delay throttle delay in milliseconds
 * @returns throttled function
 *
 * @example
 * ```typescript
 * const throttledScroll = useThrottleFn((event: Event) => {
 *   updateScrollPosition(event)
 * }, 100)
 *
 * // Usage
 * window.addEventListener('scroll', throttledScroll)
 * ```
 */
export function useThrottleFn<T extends (...args: never[]) => unknown>(
    fn: T,
    delay: number = 100
): ThrottledFunction<T> {
    let lastCall = 0
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    const cancel = () => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
    }

    const throttled = ((...args: Parameters<T>) => {
        const now = Date.now()
        const timeSinceLastCall = now - lastCall

        if (timeSinceLastCall >= delay) {
            cancel()
            fn(...args)
            lastCall = now
        } else {
            // Schedule the trailing call.
            cancel()

            timeoutId = setTimeout(() => {
                fn(...args)
                lastCall = Date.now()
                timeoutId = null
            }, delay - timeSinceLastCall)
        }
    }) as ThrottledFunction<T>

    throttled.cancel = cancel
    onScopeDispose(cancel, true)

    return throttled
}
