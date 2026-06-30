import { computed, ref } from 'vue'
import { useEventListener } from '@/composables/useEventListener'

function getNavigatorOnlineState() {
    return typeof navigator === 'undefined' ? true : navigator.onLine
}

export function useNetworkStatus() {
    const isOnline = ref(getNavigatorOnlineState())
    const wasOffline = ref(false)

    const updateOnlineStatus = () => {
        const wasOfflineBefore = !isOnline.value
        isOnline.value = getNavigatorOnlineState()

        if (wasOfflineBefore && isOnline.value) {
            wasOffline.value = true
        } else if (!isOnline.value) {
            wasOffline.value = false
        }
    }

    const resetWasOffline = () => {
        wasOffline.value = false
    }

    useEventListener(() => typeof window === 'undefined' ? null : window, 'online', updateOnlineStatus)
    useEventListener(() => typeof window === 'undefined' ? null : window, 'offline', updateOnlineStatus)

    return {
        isOnline,
        wasOffline,
        isOffline: computed(() => !isOnline.value),
        resetWasOffline,
    }
}
