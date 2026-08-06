import type { Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'

export interface PostFormLeaveGuardTarget {
    hasUnsavedChanges?: () => boolean
    isSubmissionInProgress?: () => boolean
    consumeSuccessfulSubmissionNavigation?: () => boolean
    getLeaveConfirmMessage?: () => string
    flushPendingDraft?: () => boolean
}

export type PostFormLeaveConfirm = (message: string) => boolean | Promise<boolean>

export function usePostFormLeaveGuard(
    postFormRef: Ref<PostFormLeaveGuardTarget | null>,
    fallbackMessage: string,
    confirmLeave: PostFormLeaveConfirm,
) {
    const guardNavigation = async () => {
        const form = postFormRef.value
        if (form?.consumeSuccessfulSubmissionNavigation?.()) {
            return true
        }
        const hasUnsavedChanges = form?.hasUnsavedChanges?.() ?? false
        if (form?.isSubmissionInProgress?.()) {
            return false
        }
        if (hasUnsavedChanges) {
            const message = form?.getLeaveConfirmMessage?.() ?? fallbackMessage
            if (!await confirmLeave(message)) {
                return false
            }
        }
        if (hasUnsavedChanges && form?.flushPendingDraft?.() === false) {
            return false
        }
        return true
    }

    onBeforeRouteLeave(guardNavigation)
    onBeforeRouteUpdate(guardNavigation)
}
