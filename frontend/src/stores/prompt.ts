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
    const required = ref(false)
    const errorMessage = ref('')
    const confirmVariant = ref<'primary' | 'danger'>('primary')
    const maxLength = ref<number | undefined>(undefined)
    const resolvePromise = ref<((value: string | null) => void) | null>(null)

    function open(
        msg: string,
        ttl: string = 'Input',
        placeholderText: string = '',
        confirmTxt: string = 'Confirm',
        cancelTxt: string = 'Cancel',
        options: { required?: boolean; errorMessage?: string; confirmVariant?: 'primary' | 'danger'; maxLength?: number } = {}
    ): Promise<string | null> {
        if (resolvePromise.value) {
            resolvePromise.value(null)
            resolvePromise.value = null
        }

        title.value = ttl
        message.value = msg
        placeholder.value = placeholderText
        inputValue.value = ''
        confirmText.value = confirmTxt
        cancelText.value = cancelTxt
        required.value = options.required ?? false
        errorMessage.value = options.errorMessage ?? ''
        confirmVariant.value = options.confirmVariant ?? 'primary'
        maxLength.value = options.maxLength
        isOpen.value = true

        return new Promise((resolve) => {
            resolvePromise.value = resolve
        })
    }

    function confirm() {
        if (required.value && !inputValue.value.trim()) return
        if (resolvePromise.value) {
            resolvePromise.value(inputValue.value.trim() || null)
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
        required.value = false
        errorMessage.value = ''
        confirmVariant.value = 'primary'
        maxLength.value = undefined
    }

    return {
        isOpen,
        title,
        message,
        inputValue,
        placeholder,
        confirmText,
        cancelText,
        required,
        errorMessage,
        confirmVariant,
        maxLength,
        open,
        confirm,
        cancel
    }
})
