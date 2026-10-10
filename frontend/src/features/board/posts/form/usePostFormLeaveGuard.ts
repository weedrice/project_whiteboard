import type { Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { getCurrentQuerySessionSource } from '@/queryAuthScope'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'

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
            const source = getCurrentQuerySessionSource()
            const intent = captureAuthSessionIntent(source)
            const message = leaveState.message || fallbackMessage
            const confirmed = await confirmLeave(message)
            // A boundary closes the old dialog. Let the router re-evaluate access,
            // without saving the old form under the replacement session.
            if (!isAuthSessionIntentCurrent(source, intent)) return true
            if (!confirmed) {
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
