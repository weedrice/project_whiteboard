import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * IntersectionObserver를 위한 재사용 가능한 composable
 * 
 * @param callback 교차 상태가 변경될 때 호출되는 콜백 함수
 * @param options IntersectionObserver 옵션
 * @returns targetRef와 observe/unobserve 함수
 * 
 * @example
 * ```typescript
 * // 무한 스크롤
 * const { targetRef, observe, unobserve } = useIntersectionObserver(
 *   (isIntersecting) => {
 *     if (isIntersecting && hasMore.value) {
 *       loadMore()
 *     }
 *   },
 *   { rootMargin: '200px' }
 * )
 * 
 * onMounted(() => {
 *   observe()
 * })
 * 
 * onUnmounted(() => {
 *   unobserve()
 * })
 * ```
 * 
 * @example
 * ```typescript
 * // 스크롤 감지 (플로팅 네비게이션)
 * const showFloatingNav = ref(false)
 * const { targetRef } = useIntersectionObserver(
 *   (isIntersecting) => {
 *     showFloatingNav.value = !isIntersecting
 *   },
 *   { threshold: 0 }
 * )
 * ```
 */
export function useIntersectionObserver(
    callback: (isIntersecting: boolean) => void,
    options?: IntersectionObserverInit
) {
    const targetRef = ref<HTMLElement | null>(null)
    let observer: IntersectionObserver | null = null

    /**
     * 옵저버 시작
     */
    const observe = () => {
        if (!targetRef.value) return

        // 기존 옵저버가 있으면 정리
        if (observer) {
            observer.disconnect()
        }

        observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                callback(entry.isIntersecting)
            })
        }, options)

        observer.observe(targetRef.value)
    }

    /**
     * 옵저버 중지
     */
    const unobserve = () => {
        if (observer && targetRef.value) {
            observer.unobserve(targetRef.value)
            observer.disconnect()
            observer = null
        }
    }

    // targetRef가 변경되면 자동으로 옵저버 재설정
    watch(targetRef, (newVal) => {
        unobserve()
        if (newVal) {
            observe()
        }
    })

    // 컴포넌트 언마운트 시 정리
    onUnmounted(() => {
        unobserve()
    })

    return {
        targetRef,
        observe,
        unobserve
    }
}
