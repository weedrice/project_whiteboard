import type { Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'

export interface PostFormLeaveState {
    dirty: boolean
    submitting: boolean
    message: string
}

export interface PostFormLeaveGuardTarget {
    getLeaveState: () => PostFormLeaveState
    flushDraft: () => boolean
}

export type PostFormLeaveConfirm = (message: string) => boolean | Promise<boolean>

export function usePostFormLeaveGuard(
    postFormRef: Ref<PostFormLeaveGuardTarget | null>,
    fallbackMessage: string,
    confirmLeave: PostFormLeaveConfirm,
) {
    const guardNavigation = async () => {
        const form = postFormRef.value
        if (!form) return true

        const leaveState = form.getLeaveState()
        if (leaveState.submitting) {
            return false
        }
        if (leaveState.dirty) {
            const message = leaveState.message || fallbackMessage
            if (!await confirmLeave(message)) {
                return false
            }
        }
        if (leaveState.dirty && !form.flushDraft()) {
            return false
        }
        return true
    }

    onBeforeRouteLeave(guardNavigation)
    onBeforeRouteUpdate(guardNavigation)
}
