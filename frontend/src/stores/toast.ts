import { defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import { normalizeApiErrorMessage } from '@/utils/errorHandler'

/** Same error messages are shown only once within this window. */
const SAME_MESSAGE_DEBOUNCE_MS = 5000

export interface Toast {
    id: number;
    message: string;
    type: 'info' | 'success' | 'warning' | 'error';
    duration: number;
    position: 'top-center' | 'bottom-center';
}

export const useToastStore = defineStore('toast', () => {
    const toasts = ref<Toast[]>([])
    let nextId = 0
    const removalTimers = new Map<number, {
        timer: ReturnType<typeof setTimeout> | null
        remaining: number
        startedAt: number
    }>()
    /** Last display timestamp for debouncing error toasts. */
    const lastErrorMessageShownAt = new Map<string, number>()

    const clearRemovalTimer = (id: number) => {
        const timer = removalTimers.get(id)
        if (!timer) return

        if (timer.timer) clearTimeout(timer.timer)
        removalTimers.delete(id)
    }

    const scheduleRemoval = (id: number, delay: number) => {
        const timer = setTimeout(() => {
            removalTimers.delete(id)
            removeToast(id)
        }, delay)
        removalTimers.set(id, { timer, remaining: delay, startedAt: Date.now() })
    }

    const addToast = (message: string, type: Toast['type'] = 'info', duration = 3000, position: Toast['position'] = 'top-center') => {
        const displayMessage = type === 'error' ? normalizeApiErrorMessage(message) : message
        if (type === 'error') {
            const now = Date.now()
            const lastAt = lastErrorMessageShownAt.get(displayMessage)
            if (lastAt != null && now - lastAt < SAME_MESSAGE_DEBOUNCE_MS) {
                return
            }
            lastErrorMessageShownAt.set(displayMessage, now)
            for (const [key, ts] of lastErrorMessageShownAt.entries()) {
                if (now - ts >= SAME_MESSAGE_DEBOUNCE_MS) lastErrorMessageShownAt.delete(key)
            }
        }

        const id = nextId++
        const toast: Toast = {
            id,
            message: displayMessage,
            type,
            duration,
            position
        }
        toasts.value.push(toast)

        if (duration > 0) {
            scheduleRemoval(id, duration)
        }
    }

    const pauseToast = (id: number) => {
        const removal = removalTimers.get(id)
        if (!removal?.timer) return

        clearTimeout(removal.timer)
        removal.remaining = Math.max(0, removal.remaining - (Date.now() - removal.startedAt))
        removal.timer = null
    }

    const resumeToast = (id: number) => {
        const removal = removalTimers.get(id)
        if (!removal || removal.timer) return
        if (removal.remaining <= 0) {
            removeToast(id)
            return
        }

        scheduleRemoval(id, removal.remaining)
    }

    const removeToast = (id: number) => {
        clearRemovalTimer(id)
        const index = toasts.value.findIndex(t => t.id === id)
        if (index !== -1) {
            toasts.value.splice(index, 1)
        }
    }

    onScopeDispose(() => {
        removalTimers.forEach(({ timer }) => {
            if (timer) clearTimeout(timer)
        })
        removalTimers.clear()
        lastErrorMessageShownAt.clear()
    })

    return {
        toasts,
        addToast,
        removeToast,
        pauseToast,
        resumeToast,
    }
})
