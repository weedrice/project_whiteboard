import { onScopeDispose, ref, watch, type Ref } from 'vue'

export type DebouncedFunction<T extends (...args: never[]) => unknown> = ((...args: Parameters<T>) => void) & {
    cancel: () => void
}

/**
 * Returns a debounced ref that updates after the delay.
 *
 * @param value source ref to debounce
 * @param delay debounce delay in milliseconds
 * @returns debounced ref value
 *
 * @example
 * ```typescript
 * const searchQuery = ref('')
 * const debouncedQuery = useDebounce(searchQuery, 300)
 *
 * watch(debouncedQuery, (value) => {
 *   // Runs after 300ms without changes.
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

    onScopeDispose(() => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
    }, true)

    return debounced
}

/**
 * Returns a debounced function with a cancel helper.
 *
 * @param fn function to debounce
 * @param delay debounce delay in milliseconds
 * @returns debounced function
 *
 * @example
 * ```typescript
 * const debouncedSearch = useDebounceFn((query: string) => {
 *   performSearch(query)
 * }, 300)
 *
 * // Usage
 * debouncedSearch('search term')
 * ```
 */
export function useDebounceFn<T extends (...args: never[]) => unknown>(
    fn: T,
    delay: number = 300
): DebouncedFunction<T> {
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    const cancel = () => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
    }

    const debounced = ((...args: Parameters<T>) => {
        cancel()
        timeoutId = setTimeout(() => {
            fn(...args)
            timeoutId = null
        }, delay)
    }) as DebouncedFunction<T>

    debounced.cancel = cancel
    onScopeDispose(cancel, true)

    return debounced
}
