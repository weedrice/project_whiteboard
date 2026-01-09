import { ref, computed, onMounted, onUnmounted } from 'vue'

/**
 * 네트워크 상태 모니터링 Composable
 */
export function useNetworkStatus() {
    const isOnline = ref(navigator.onLine)
    const wasOffline = ref(false)

    const updateOnlineStatus = () => {
        const wasOfflineBefore = !isOnline.value
        isOnline.value = navigator.onLine

        if (wasOfflineBefore && isOnline.value) {
            // 오프라인에서 온라인으로 전환
            wasOffline.value = true
            // 페이지 새로고침 (선택적)
            // window.location.reload()
        }
    }

    onMounted(() => {
        window.addEventListener('online', updateOnlineStatus)
        window.addEventListener('offline', updateOnlineStatus)
    })

    onUnmounted(() => {
        window.removeEventListener('online', updateOnlineStatus)
        window.removeEventListener('offline', updateOnlineStatus)
    })

    return {
        isOnline,
        wasOffline,
        isOffline: computed(() => !isOnline.value)
    }
}
