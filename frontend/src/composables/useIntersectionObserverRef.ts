import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * IntersectionObserver를 ref와 함께 사용하는 간단한 composable
 * 
 * @param targetRef 관찰할 요소의 ref
 * @param options IntersectionObserver 옵션
 * @returns isIntersecting ref
 */
export function useIntersectionObserver(
    targetRef: Ref<HTMLElement | null>,
    options?: IntersectionObserverInit
) {
    const isIntersecting = ref(false)
    let observer: IntersectionObserver | null = null

    const observe = () => {
        if (!targetRef.value) return

        // 기존 옵저버가 있으면 정리
        if (observer) {
            observer.disconnect()
        }

        observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                isIntersecting.value = entry.isIntersecting
            })
        }, options)

        observer.observe(targetRef.value)
    }

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
    }, { immediate: true })

    // 컴포넌트 언마운트 시 정리
    onUnmounted(() => {
        unobserve()
    })

    return {
        isIntersecting
    }
}
