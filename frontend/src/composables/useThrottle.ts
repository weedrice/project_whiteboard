import { ref, watch, onUnmounted, onScopeDispose, type Ref } from 'vue'

export type ThrottledFunction<T extends (...args: any[]) => any> = ((...args: Parameters<T>) => void) & {
    cancel: () => void
}

/**
 * 스로틀링을 위한 composable
 * 
 * @param value 스로틀링할 값
 * @param delay 지연 시간 (밀리초)
 * @returns 스로틀링된 값
 * 
 * @example
 * ```typescript
 * const scrollY = ref(0)
 * const throttledScrollY = useThrottle(scrollY, 100)
 * 
 * watch(throttledScrollY, (value) => {
 *   // 최대 100ms마다 실행됨
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
            // 남은 시간 후에 업데이트
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

    onUnmounted(() => {
        if (timeoutId) {
            clearTimeout(timeoutId)
        }
    })

    return throttled
}

/**
 * 스로틀링된 함수를 반환하는 composable
 * 
 * @param fn 스로틀링할 함수
 * @param delay 지연 시간 (밀리초)
 * @returns 스로틀링된 함수
 * 
 * @example
 * ```typescript
 * const throttledScroll = useThrottleFn((event: Event) => {
 *   updateScrollPosition(event)
 * }, 100)
 * 
 * // 사용
 * window.addEventListener('scroll', throttledScroll)
 * ```
 */
export function useThrottleFn<T extends (...args: any[]) => any>(
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
            // 남은 시간 후에 실행
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
