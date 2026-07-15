import type { Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

export interface PostFormLeaveGuardTarget {
    hasUnsavedChanges?: () => boolean
    getLeaveConfirmMessage?: () => string
}

export type PostFormLeaveConfirm = (message: string) => boolean | Promise<boolean>

export function usePostFormLeaveGuard(
    postFormRef: Ref<PostFormLeaveGuardTarget | null>,
    fallbackMessage: string,
    confirmLeave: PostFormLeaveConfirm,
) {
    onBeforeRouteLeave(async () => {
        const form = postFormRef.value
        if (form?.hasUnsavedChanges?.()) {
            const message = form.getLeaveConfirmMessage?.() ?? fallbackMessage
            if (!await confirmLeave(message)) {
                return false
            }
        }
        return true
    })
}
