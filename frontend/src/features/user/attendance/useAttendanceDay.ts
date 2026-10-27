import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'

const DAY_MS = 24 * 60 * 60 * 1000
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

// Attendance follows the server's Asia/Seoul date, regardless of browser timezone.
const attendanceDay = () => new Date(Date.now() + KST_OFFSET_MS).toISOString().slice(0, 10)

export function useAttendanceDay(enabled: Ref<boolean>) {
    const today = ref(attendanceDay())
    if (!getCurrentScope() || typeof window === 'undefined') return today

    let timer: ReturnType<typeof setTimeout> | undefined
    const synchronize = () => {
        clearTimeout(timer)
        today.value = attendanceDay()
        timer = setTimeout(synchronize, DAY_MS - ((Date.now() + KST_OFFSET_MS) % DAY_MS))
    }
    const onVisible = () => {
        if (document.visibilityState === 'visible') synchronize()
    }

    synchronize()
    watch(enabled, synchronize, { flush: 'sync' })
    // Background timers can be suspended while a device sleeps or a tab is frozen.
    window.addEventListener('focus', synchronize)
    window.addEventListener('pageshow', synchronize)
    document.addEventListener('visibilitychange', onVisible)
    onScopeDispose(() => {
        clearTimeout(timer)
        window.removeEventListener('focus', synchronize)
        window.removeEventListener('pageshow', synchronize)
        document.removeEventListener('visibilitychange', onVisible)
    })
    return today
}
