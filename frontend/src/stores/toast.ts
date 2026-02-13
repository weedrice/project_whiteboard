import { defineStore } from 'pinia'
import { ref } from 'vue'
import { normalizeApiErrorMessage } from '@/utils/errorHandler'

/** 동일 메시지 디듀핑: 이 시간(ms) 내 동일 메시지는 한 번만 표시 */
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
    /** type=error일 때 동일 메시지 마지막 표시 시각 (message -> timestamp) */
    const lastErrorMessageShownAt = new Map<string, number>()

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
            setTimeout(() => {
                removeToast(id)
            }, duration)
        }
    }

    const removeToast = (id: number) => {
        const index = toasts.value.findIndex(t => t.id === id)
        if (index !== -1) {
            toasts.value.splice(index, 1)
        }
    }

    return {
        toasts,
        addToast,
        removeToast
    }
})
