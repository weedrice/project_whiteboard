import { computed, ref } from 'vue'

type DraftBlockingStatus = 'active' | 'conflict' | 'protected' | 'deleted'

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
    draftConflict: createFlag('conflict'),
    draftProtected: createFlag('protected'),
    draftDeleted: createFlag('deleted'),
    reset,
  }
}
