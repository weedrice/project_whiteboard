import { computed, onScopeDispose, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'

/** One confirmation owner for closing, changing conversations, and leaving the mailbox. */
export function useMailboxDraftGuard(
    replyContent: Ref<string>,
    isSending: Readonly<Ref<boolean>>,
    partnerId: Readonly<Ref<number | undefined>>,
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

    watch([replyContent, isSending, partnerId], cancelConfirmation, { flush: 'sync' })
    onScopeDispose(cancelConfirmation)

    const { confirmNavigation } = useUnsavedChangesGuard(
        computed(() => replyContent.value.trim().length > 0),
        isSending,
        () => t('user.message.discardConfirm'),
        async (message) => (await operation.run('discard', async (scope) => {
            const requestedRevision = revision
            const currentController = new AbortController()
            controller = currentController
            try {
                const approved = await confirm(message, undefined, undefined, undefined, {
                    signal: scope.withRequestConfig({ signal: currentController.signal }).signal,
                })
                if (!approved || !scope.isCurrent() || requestedRevision !== revision || isSending.value) return false
                replyContent.value = ''
                return true
            } finally {
                if (controller === currentController) controller = null
            }
        })) ?? false,
    )

    return { confirmNavigation }
}
