import { onScopeDispose, watch, type Ref } from 'vue'
import { onBeforeRouteUpdate } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useConfirm } from '@/composables/useConfirm'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'

export function useCommentDraftGuard(
    content: Readonly<Ref<string>>,
    hasUnsavedChanges: Readonly<Ref<boolean>>,
    isSubmitting: Readonly<Ref<boolean>>,
    identity: Readonly<Ref<string>>,
) {
    const { t } = useI18n()
    const { confirm } = useConfirm()
    const operation = useAuthScopedOperation()
    let revision = 0
    let controller: AbortController | null = null

    function cancelConfirmation() {
        revision += 1
        controller?.abort()
        controller = null
    }

    watch([content, isSubmitting, identity], cancelConfirmation, { flush: 'sync' })
    onScopeDispose(cancelConfirmation)

    const { confirmNavigation } = useUnsavedChangesGuard(
        hasUnsavedChanges,
        isSubmitting,
        () => t('comment.discardConfirm'),
        async (message) => (await operation.run('discard', async (scope) => {
            const requestedRevision = revision
            const currentController = new AbortController()
            controller = currentController
            try {
                const approved = await confirm(message, undefined, undefined, undefined, {
                    signal: scope.withRequestConfig({ signal: currentController.signal }).signal,
                })
                // Another open comment form may still reject this navigation. Do not
                // clear any content until Vue Router actually unmounts this form.
                return approved && scope.isCurrent() && requestedRevision === revision && !isSubmitting.value
            } finally {
                if (controller === currentController) controller = null
            }
        })) ?? false,
    )

    // Hash links within the current post do not dispose the composer.
    onBeforeRouteUpdate((to, from) => to.path === from.path ? true : confirmNavigation())

    return { confirmDiscard: confirmNavigation }
}
