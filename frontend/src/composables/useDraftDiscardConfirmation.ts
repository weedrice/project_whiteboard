import { onScopeDispose, watch, type WatchSource } from 'vue'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'

interface DraftDiscardOptions {
  draftState: WatchSource<unknown>
  isDirty: () => boolean
  canClose?: () => boolean
  message: () => string
  close: () => void
}

/** Confirms user dismissal while preserving newer drafts and unrelated confirmations. */
export function useDraftDiscardConfirmation(options: DraftDiscardOptions) {
  const { confirm } = useConfirm()
  const operation = useAuthScopedOperation()
  let revision = 0
  let controller: AbortController | null = null

  function cancelConfirmation() {
    revision += 1
    controller?.abort()
    controller = null
  }

  watch(options.draftState, cancelConfirmation, { deep: true, flush: 'sync' })
  onScopeDispose(cancelConfirmation)

  function requestClose() {
    if (options.canClose?.() === false) return
    return operation.run('discard', async (scope) => {
      const requestedRevision = revision
      const currentController = new AbortController()
      controller = currentController
      try {
        if (options.isDirty() && !await confirm(
          options.message(), undefined, undefined, undefined,
          { signal: scope.withRequestConfig({ signal: currentController.signal }).signal },
        )) return
        if (!scope.isCurrent() || requestedRevision !== revision || options.canClose?.() === false) return
        options.close()
      } finally {
        if (controller === currentController) controller = null
      }
    })
  }

  return { requestClose }
}
