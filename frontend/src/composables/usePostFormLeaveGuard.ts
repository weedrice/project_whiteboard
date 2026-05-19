import type { Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

export interface PostFormLeaveGuardTarget {
    hasUnsavedChanges?: () => boolean
    getLeaveConfirmMessage?: () => string
}

export function usePostFormLeaveGuard(
    postFormRef: Ref<PostFormLeaveGuardTarget | null>,
    fallbackMessage: string,
) {
    onBeforeRouteLeave((_to, _from, next) => {
        const form = postFormRef.value
        if (form?.hasUnsavedChanges?.()) {
            const message = form.getLeaveConfirmMessage?.() ?? fallbackMessage
            if (!window.confirm(message)) {
                next(false)
                return
            }
        }
        next()
    })
}
