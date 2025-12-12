import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePromptStore = defineStore('prompt', () => {
    const isOpen = ref(false)
    const title = ref('')
    const message = ref('')
    const inputValue = ref('')
    const placeholder = ref('')
    const confirmText = ref('Confirm')
    const cancelText = ref('Cancel')
    const resolvePromise = ref<((value: string | null) => void) | null>(null)

    function open(
        msg: string,
        ttl: string = 'Input',
        placeholderText: string = '',
        confirmTxt: string = 'Confirm',
        cancelTxt: string = 'Cancel'
    ): Promise<string | null> {
        title.value = ttl
        message.value = msg
        placeholder.value = placeholderText
        inputValue.value = ''
        confirmText.value = confirmTxt
        cancelText.value = cancelTxt
        isOpen.value = true

        return new Promise((resolve) => {
            resolvePromise.value = resolve
        })
    }

    function confirm() {
        if (resolvePromise.value) {
            resolvePromise.value(inputValue.value || null)
        }
        close()
    }

    function cancel() {
        if (resolvePromise.value) {
            resolvePromise.value(null)
        }
        close()
    }

    function close() {
        isOpen.value = false
        resolvePromise.value = null
        title.value = ''
        message.value = ''
        inputValue.value = ''
        placeholder.value = ''
    }

    return {
        isOpen,
        title,
        message,
        inputValue,
        placeholder,
        confirmText,
        cancelText,
        open,
        confirm,
        cancel
    }
})
