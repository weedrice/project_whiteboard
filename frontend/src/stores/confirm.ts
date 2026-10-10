import { defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'

export const useConfirmStore = defineStore('confirm', () => {
    const authStore = useAuthStore()
    let scope: AuthSessionScope | null = null
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
        cancel()
        scope = createAuthSessionScope(authStore)

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
            resolvePromise.value(scope?.isCurrent() === true)
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
        scope?.dispose()
        scope = null
        isOpen.value = false
        resolvePromise.value = null
        title.value = ''
        message.value = ''
    }

    useAuthSessionReset(cancel, authStore)
    onScopeDispose(cancel)

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
