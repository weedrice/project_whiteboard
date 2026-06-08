import { ref, computed } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

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
        } else if (!isOnline.value) {
            // 온라인에서 오프라인으로 전환 시 wasOffline 리셋
            wasOffline.value = false
        }
    }

    const resetWasOffline = () => {
        wasOffline.value = false
    }

    useEventListener(() => window, 'online', updateOnlineStatus)
    useEventListener(() => window, 'offline', updateOnlineStatus)

    return {
        isOnline,
        wasOffline,
        isOffline: computed(() => !isOnline.value),
        resetWasOffline
    }
}
