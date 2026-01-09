import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * 디바운싱을 위한 composable
 * 
 * @param value 디바운싱할 값
 * @param delay 지연 시간 (밀리초)
 * @returns 디바운싱된 값
 * 
 * @example
 * ```typescript
 * const searchQuery = ref('')
 * const debouncedQuery = useDebounce(searchQuery, 300)
 * 
 * watch(debouncedQuery, (value) => {
 *   // 300ms 후에 실행됨
 *   performSearch(value)
 * })
 * ```
 */
export function useDebounce<T>(value: Ref<T>, delay: number = 300): Ref<T> {
    const debounced = ref(value.value) as Ref<T>
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    watch(value, (newValue) => {
        if (timeoutId) {
            clearTimeout(timeoutId)
        }

        timeoutId = setTimeout(() => {
            debounced.value = newValue
            timeoutId = null
        }, delay)
    }, { immediate: true })

    onUnmounted(() => {
        if (timeoutId) {
            clearTimeout(timeoutId)
        }
    })

    return debounced
}

/**
 * 디바운싱된 함수를 반환하는 composable
 * 
 * @param fn 디바운싱할 함수
 * @param delay 지연 시간 (밀리초)
 * @returns 디바운싱된 함수
 * 
 * @example
 * ```typescript
 * const debouncedSearch = useDebounceFn((query: string) => {
 *   performSearch(query)
 * }, 300)
 * 
 * // 사용
 * debouncedSearch('search term')
 * ```
 */
export function useDebounceFn<T extends (...args: any[]) => any>(
    fn: T,
    delay: number = 300
): (...args: Parameters<T>) => void {
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    return (...args: Parameters<T>) => {
        if (timeoutId) {
            clearTimeout(timeoutId)
        }

        timeoutId = setTimeout(() => {
            fn(...args)
            timeoutId = null
        }, delay)
    }
}
