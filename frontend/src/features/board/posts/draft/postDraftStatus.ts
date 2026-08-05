import { computed, ref } from 'vue'

export type DraftBlockingStatus = 'active' | 'conflict' | 'protected' | 'deleted'

export type DraftStatus =
  | 'disabled'
  | 'conflict'
  | 'protected'
  | 'deleted'
  | 'restoring'
  | 'saving'
  | 'restore-failed'
  | 'save-failed'
  | 'dirty'
  | 'clean'

export function createDraftBlockingStatusController(initial: DraftBlockingStatus = 'active') {
  const status = ref<DraftBlockingStatus>(initial)

  const createFlag = (target: Exclude<DraftBlockingStatus, 'active'>) => computed({
    get: () => status.value === target,
    set: (enabled: boolean) => {
      if (enabled) {
        status.value = target
      } else if (status.value === target) {
        status.value = 'active'
      }
    },
  })

  const reset = () => {
    status.value = 'active'
  }

  return {
    status,
    draftConflict: createFlag('conflict'),
    draftProtected: createFlag('protected'),
    draftDeleted: createFlag('deleted'),
    reset,
  }
}

interface ResolveDraftStatusOptions {
  enabled: boolean
  blockingStatus: DraftBlockingStatus
  isRestoring: boolean
  isSaving: boolean
  restoreFailed: boolean
  saveFailed: boolean
  dirty: boolean
}

export function resolveDraftStatus({
  enabled,
  blockingStatus,
  isRestoring,
  isSaving,
  restoreFailed,
  saveFailed,
  dirty,
}: ResolveDraftStatusOptions): DraftStatus {
  if (!enabled) return 'disabled'
  if (blockingStatus !== 'active') return blockingStatus
  if (isRestoring) return 'restoring'
  if (isSaving) return 'saving'
  if (restoreFailed) return 'restore-failed'
  if (saveFailed) return 'save-failed'
  return dirty ? 'dirty' : 'clean'
}
