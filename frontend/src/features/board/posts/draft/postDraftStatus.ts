import { computed, ref } from 'vue'

export type DraftAvailability =
  | { type: 'active' }
  | { type: 'conflict' }
  | { type: 'protected', canFork: boolean }
  | { type: 'deleted' }

export type DraftOperation =
  | { type: 'idle' }
  | { type: 'restoring' }
  | { type: 'saving' }
  | { type: 'retry-wait', attempt: number, max: number }

export type DraftPersistence =
  | { type: 'none' }
  | { type: 'saved', scope: 'server' | 'browser', at: string }
  | { type: 'failed', target: 'server' | 'browser', exhausted: boolean }

export interface DraftSessionIdentity {
  draftId: number | null
  clientDraftKey: string
  version: number | null
  updatedAt: string | null
}

type DraftRequestKind = 'save' | 'recovery'

type DraftBlockingStatus = DraftAvailability['type']

const initialAvailability = (type: DraftBlockingStatus): DraftAvailability => (
  type === 'protected' ? { type, canFork: false } : { type }
)

export function createDraftSessionStatusController(
  initial: DraftBlockingStatus = 'active',
  createClientKey: () => string = () => globalThis.crypto?.randomUUID?.()
    ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
) {
  const availability = ref<DraftAvailability>(initialAvailability(initial))
  const operation = ref<DraftOperation>({ type: 'idle' })
  const persistence = ref<DraftPersistence>({ type: 'none' })
  const restoreFailed = ref(false)
  const draftId = ref<number | null>(null)
  const clientDraftKey = ref(createClientKey())
  const draftVersion = ref<number | null>(null)
  const updatedAt = ref<string | null>(null)
  let generation = 0
  let localRevision = 0
  let persistedRevision = 0
  let lastRemoteLocalChangeAt = 0
  const requests: Record<DraftRequestKind, AbortController | null> = {
    save: null,
    recovery: null,
  }

  const setAvailability = (next: DraftAvailability) => {
    availability.value = next
  }

  const createFlag = (target: Exclude<DraftBlockingStatus, 'active'>) => computed({
    get: () => availability.value.type === target,
    set: (enabled: boolean) => {
      if (enabled) {
        availability.value = target === 'protected'
          ? {
              type: 'protected',
              canFork: availability.value.type === 'protected' && availability.value.canFork,
            }
          : { type: target }
      } else if (availability.value.type === target) {
        availability.value = { type: 'active' }
      }
    },
  })

  const protectedDraftForkAvailable = computed({
    get: () => availability.value.type === 'protected' && availability.value.canFork,
    set: (canFork: boolean) => {
      if (availability.value.type === 'protected') {
        availability.value = { type: 'protected', canFork }
      } else if (canFork) {
        availability.value = { type: 'protected', canFork: true }
      }
    },
  })

  const isRestoringDraft = computed({
    get: () => operation.value.type === 'restoring',
    set: (enabled: boolean) => {
      if (enabled) operation.value = { type: 'restoring' }
      else if (operation.value.type === 'restoring') operation.value = { type: 'idle' }
    },
  })

  const isSavingDraft = computed({
    get: () => operation.value.type === 'saving',
    set: (enabled: boolean) => {
      if (enabled) operation.value = { type: 'saving' }
      else if (operation.value.type === 'saving') operation.value = { type: 'idle' }
    },
  })

  const lastSaveFailed = computed({
    get: () => persistence.value.type === 'failed',
    set: (failed: boolean) => {
      if (failed) persistence.value = {
        type: 'failed',
        target: persistence.value.type === 'failed' ? persistence.value.target : 'server',
        exhausted: false,
      }
      else if (persistence.value.type === 'failed') {
        persistence.value = { type: 'none' }
      }
    },
  })

  const lastSavedAt = computed({
    get: () => persistence.value.type === 'saved' ? persistence.value.at : null,
    set: (at: string | null) => {
      if (at == null) {
        if (persistence.value.type === 'saved') persistence.value = { type: 'none' }
        return
      }
      const scope = persistence.value.type === 'saved' ? persistence.value.scope : 'server'
      persistence.value = { type: 'saved', scope, at }
    },
  })

  const lastSaveScope = computed({
    get: () => persistence.value.type === 'saved' ? persistence.value.scope : null,
    set: (scope: 'server' | 'browser' | null) => {
      if (scope == null) {
        if (persistence.value.type === 'saved') persistence.value = { type: 'none' }
        return
      }
      const at = persistence.value.type === 'saved'
        ? persistence.value.at
        : new Date().toISOString()
      persistence.value = { type: 'saved', scope, at }
    },
  })

  const setRetryWait = (attempt: number, max: number) => {
    operation.value = { type: 'retry-wait', attempt, max }
    if (persistence.value.type === 'failed') {
      persistence.value = { ...persistence.value, exhausted: attempt >= max }
    }
  }

  const setPersistenceFailure = (target: 'server' | 'browser', exhausted = false) => {
    persistence.value = { type: 'failed', target, exhausted }
  }

  const startRequest = (kind: DraftRequestKind) => {
    requests[kind]?.abort()
    const controller = new AbortController()
    requests[kind] = controller
    return controller
  }

  const finishRequest = (kind: DraftRequestKind, controller: AbortController) => {
    if (requests[kind] !== controller) return false
    requests[kind] = null
    return true
  }

  const isRequestCurrent = (kind: DraftRequestKind, controller: AbortController) => (
    requests[kind] === controller && !controller.signal.aborted
  )

  const invalidatePendingWork = (
    resetRevisions = true,
    resetQueuedWork?: () => void,
  ) => {
    generation++
    requests.save?.abort()
    requests.recovery?.abort()
    requests.save = null
    requests.recovery = null
    resetQueuedWork?.()
    if (resetRevisions) {
      localRevision = 0
      persistedRevision = 0
      lastRemoteLocalChangeAt = 0
    }
    return generation
  }

  const resetIdentity = () => {
    draftId.value = null
    draftVersion.value = null
    clientDraftKey.value = createClientKey()
    updatedAt.value = null
    lastRemoteLocalChangeAt = 0
  }

  const reset = () => {
    availability.value = { type: 'active' }
    operation.value = { type: 'idle' }
    persistence.value = { type: 'none' }
    restoreFailed.value = false
  }

  return {
    availability,
    operation,
    persistence,
    identity: {
      draftId,
      clientDraftKey,
      version: draftVersion,
      updatedAt,
    },
    draftConflict: createFlag('conflict'),
    draftProtected: createFlag('protected'),
    draftDeleted: createFlag('deleted'),
    protectedDraftForkAvailable,
    isRestoringDraft,
    isSavingDraft,
    restoreFailed,
    lastSaveFailed,
    lastSavedAt,
    lastSaveScope,
    setAvailability,
    setRetryWait,
    setPersistenceFailure,
    getGeneration: () => generation,
    getLocalRevision: () => localRevision,
    incrementLocalRevision: () => ++localRevision,
    markCurrentRevisionPersisted: () => { persistedRevision = localRevision },
    setPersistedRevision: (revision: number) => { persistedRevision = revision },
    getPersistedRevision: () => persistedRevision,
    getLastRemoteLocalChangeAt: () => lastRemoteLocalChangeAt,
    setLastRemoteLocalChangeAt: (value: number) => { lastRemoteLocalChangeAt = value },
    startRequest,
    finishRequest,
    isRequestCurrent,
    getRequestSignal: (kind: DraftRequestKind) => requests[kind]?.signal,
    invalidatePendingWork,
    resetIdentity,
    reset,
  }
}

export const createDraftBlockingStatusController = createDraftSessionStatusController
