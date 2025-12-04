import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
    const toasts = ref([])
    let nextId = 0

    const addToast = (message, type = 'info', duration = 3000) => {
        const id = nextId++
        const toast = {
            id,
            message,
            type,
            duration
        }
        toasts.value.push(toast)

        if (duration > 0) {
            setTimeout(() => {
                removeToast(id)
            }, duration)
        }
    }

    const removeToast = (id) => {
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
