import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useConfirmStore = defineStore('confirm', () => {
    const isOpen = ref(false)
    const title = ref('')
    const message = ref('')
    const confirmText = ref('Confirm')
    const cancelText = ref('Cancel')
    const resolvePromise = ref<((value: boolean) => void) | null>(null)

    function open(
        msg: string,
        ttl: string = 'Confirm',
        confirmTxt: string = 'Confirm',
        cancelTxt: string = 'Cancel'
    ): Promise<boolean> {
        title.value = ttl
        message.value = msg
        confirmText.value = confirmTxt
        cancelText.value = cancelTxt
        isOpen.value = true

        return new Promise((resolve) => {
            resolvePromise.value = resolve
        })
    }

    function confirm() {
        if (resolvePromise.value) {
            resolvePromise.value(true)
        }
        close()
    }

    function cancel() {
        if (resolvePromise.value) {
            resolvePromise.value(false)
        }
        close()
    }

    function close() {
        isOpen.value = false
        resolvePromise.value = null
        title.value = ''
        message.value = ''
    }

    return {
        isOpen,
        title,
        message,
        confirmText,
        cancelText,
        open,
        confirm,
        cancel
    }
})
