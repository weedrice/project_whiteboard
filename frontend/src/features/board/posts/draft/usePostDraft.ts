import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import type { PostDraftData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { getRetryAfterMs } from '@/api/retryAfter'
import { usePost } from '@/features/board/posts/queries/usePost'
import type { DraftPost } from '@/types'
import { Storage } from '@/utils/storage'
import logger from '@/utils/logger'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import {
    getDraftUpdatedAt,
    hasSameDraftContent,
    isDraftMissingError,
    isDraftProtectedError,
    isDraftOutdatedError,
    isMatchingLoadedDraft,
    loadDraftById,
    resolveDraftRecoverySnapshot,
    type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'
import {
    createDraftRecoverySnapshot,
    createStoredSavedDraftSnapshot,
    hasBrowserDraftContent,
    hasMeaningfulDraftContent,
    stripDraftServerIdentity,
} from '@/features/board/posts/draft/postDraftSnapshot'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import {
    cleanupExpiredDraftTombstones,
    getDraftTombstoneKey,
    isDraftDeletedLocally,
    markDraftDeletedLocally,
    registerDraftDeletedListener,
} from '@/features/board/posts/draft/postDraftTombstone'
import {
    cleanupExpiredDraftSnapshots,
    loadStoredDraftSnapshot,
    migrateStoredDraftSnapshot,
    parseDraftRecoverySnapshot,
    storeDraftSnapshotWithBudgetResult,
} from '@/features/board/posts/draft/postDraftLifecycle'
import {
    matchesDraftScheduledEvent,
    publishDraftScheduledEvent,
    registerDraftScheduledListener,
} from '@/features/board/posts/draft/postDraftScheduledEvent'

export type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
export type DraftSaveScope = 'server' | 'browser'

interface UsePostDraftOptions {
    enabled: Ref<boolean>
    storageKey: Ref<string>
    resolveStorageKey?: (draftId: number) => string
    ownerId?: Ref<string | number | null | undefined>
    preferredDraftId?: Ref<number | null>
    buildPayload: () => PostDraftData
    applyDraft: (draft: DraftRecoverySnapshot) => void
    prepareRecoveredSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
    onSaved?: () => void
    onServerSaved?: (payload: PostDraftData, savedDraft: DraftPost) => void
    onServerReferencesReset?: (savedDraft: DraftPost) => void
    prepareStaleSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
    onStaleReferencesReset?: () => void
    canPersist?: () => boolean
}

const AUTOSAVE_DELAY_MS = 1500
const SAVE_RETRY_BASE_DELAY_MS = 1000
const SAVE_RETRY_MAX_DELAY_MS = 30_000
const SAVE_RETRY_MAX_ATTEMPTS = 5

export const isTransientDraftSaveError = (error: unknown) => {
    if (!isAxiosError(error)) return false
    const status = error.response?.status
    return status == null || status === 429 || status >= 500
}

const getSaveRetryDelay = (attempt: number) => {
    const exponentialDelay = Math.min(
        SAVE_RETRY_BASE_DELAY_MS * 2 ** Math.max(0, attempt - 1),
        SAVE_RETRY_MAX_DELAY_MS,
    )
    const jitter = 0.8 + Math.random() * 0.4
    return Math.min(Math.round(exponentialDelay * jitter), SAVE_RETRY_MAX_DELAY_MS)
}

export function usePostDraft(options: UsePostDraftOptions) {
    let requestController: AbortController | null = null
    const { useSaveDraft, useDeleteDraft } = usePost()
    if (typeof useSaveDraft !== 'function' || typeof useDeleteDraft !== 'function') {
        throw new Error('Draft mutations are not available.')
    }
    const resolveRequestConfig = () => requestController
        ? { signal: requestController.signal, skipGlobalErrorHandler: true }
        : undefined
    const saveDraftMutation = useSaveDraft(resolveRequestConfig)
    const deleteDraftMutation = useDeleteDraft(resolveRequestConfig)

    const draftId = ref<number | null>(null)
    const draftVersion = ref<number | null>(null)
    const updatedAt = ref<string | null>(null)
    const lastSavedAt = ref<string | null>(null)
    const lastSaveScope = ref<DraftSaveScope | null>(null)
    const lastSaveFailed = ref(false)
    const lastLocalSaveFailed = ref(false)
    const lastLocalRollbackFailed = ref(false)
    const restoreFailed = ref(false)
    const multipleDraftsFound = ref(false)
    const isRestoringDraft = ref(false)
    const draftConflict = ref(false)
    const draftProtected = ref(false)
    const draftDeleted = ref(false)
    const staleReferencesReset = ref(false)
    const contractValidationFailed = ref(false)
    const saveRetryAttempt = ref(0)
    const saveRetryScheduled = ref(false)
    const saveRetryExhausted = ref(false)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let saveRetryTimer: ReturnType<typeof setTimeout> | null = null
    let saveRetryDueAt: number | null = null
    let savePromise: Promise<DraftPost | null> | null = null
    let saveQueued = false
    let localRevision = 0
    let persistedRevision = 0
    let sessionGeneration = 0
    const createClientKey = () => typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const clientInstanceId = createClientKey()
    const clientDraftKey = ref(createClientKey())
    const activeStorageKey = computed(() => draftId.value != null
        ? options.resolveStorageKey?.(draftId.value) ?? options.storageKey.value
        : options.storageKey.value)

    watch(activeStorageKey, (nextKey, previousKey) => {
        if (draftId.value == null || nextKey === previousKey) return
        migrateStoredDraftSnapshot(
            previousKey,
            nextKey,
            draftId.value,
            clientDraftKey.value,
        )
    }, { flush: 'sync' })

    const clearAutosaveTimer = () => {
        if (autosaveTimer) {
            clearTimeout(autosaveTimer)
            autosaveTimer = null
        }
    }

    const resetDraftTracking = () => {
        draftId.value = null
        draftVersion.value = null
        clientDraftKey.value = createClientKey()
        updatedAt.value = null
        lastSavedAt.value = null
    }

    const invalidatePendingSaves = () => {
        sessionGeneration++
        requestController?.abort()
        requestController = null
        savePromise = null
        saveQueued = false
        localRevision = 0
        persistedRevision = 0
    }

    const storeLocalSnapshot = (snapshot: DraftRecoverySnapshot) => {
        const result = storeDraftSnapshotWithBudgetResult(activeStorageKey.value, {
            ...snapshot,
            clientDraftKey: snapshot.clientDraftKey ?? clientDraftKey.value,
            version: snapshot.version ?? draftVersion.value ?? undefined,
            clientInstanceId,
        })
        const stored = result.stored
        if (result.rollbackFailedCount > 0 && !lastLocalRollbackFailed.value) {
            logger.error('Draft local snapshot rollback failed.', {
                event: 'draft_local_snapshot_rollback_failed',
                failedCount: result.rollbackFailedCount,
            })
            void reportDraftOperationalEvent('local_storage_rollback_failed', {
                failedCount: result.rollbackFailedCount,
            })
        }
        if (!stored && !lastLocalSaveFailed.value) {
            logger.error('Draft local snapshot storage failed.', {
                event: 'draft_local_snapshot_write_failed',
            })
            void reportDraftOperationalEvent('local_storage_write_failed')
        }
        lastLocalRollbackFailed.value = result.rollbackFailedCount > 0
        lastLocalSaveFailed.value = !stored
        return stored
    }

    const writeLocalSnapshot = () => {
        if (!options.enabled.value) return
        localRevision++
        contractValidationFailed.value = options.canPersist?.() === false
        const snapshot = {
            ...createDraftRecoverySnapshot(options.buildPayload(), draftId.value, updatedAt.value),
            contractValidationFailed: contractValidationFailed.value,
        }
        return storeLocalSnapshot(snapshot)
    }

    const removeLocalSnapshot = () => {
        const removed = Storage.remove(activeStorageKey.value)
        if (!removed) void reportDraftOperationalEvent('local_storage_remove_failed')
        return removed
    }

    const clearSaveRetry = (resetAttempt = true) => {
        if (saveRetryTimer) {
            clearTimeout(saveRetryTimer)
            saveRetryTimer = null
        }
        saveRetryDueAt = null
        saveRetryScheduled.value = false
        if (resetAttempt) {
            saveRetryAttempt.value = 0
            saveRetryExhausted.value = false
        }
    }

    const transitionToDeletedDraft = () => {
        const deletedDraftId = draftId.value
        if (deletedDraftId != null && options.ownerId?.value != null) {
            if (!markDraftDeletedLocally(options.ownerId.value, deletedDraftId)) {
                void reportDraftOperationalEvent('tombstone_write_failed')
            }
        }
        clearAutosaveTimer()
        clearSaveRetry()
        resetDraftTracking()
        draftDeleted.value = true
        staleReferencesReset.value = true
        draftConflict.value = false
        draftProtected.value = false
        lastSaveFailed.value = false
        const strippedSnapshot = stripDraftServerIdentity(
            createDraftRecoverySnapshot(options.buildPayload(), null, null),
        )
        const staleSnapshot = options.prepareStaleSnapshot?.(strippedSnapshot) ?? strippedSnapshot
        options.applyDraft(staleSnapshot)
        options.onStaleReferencesReset?.()
        storeLocalSnapshot(staleSnapshot)
    }

    const transitionToProtectedDraft = () => {
        clearAutosaveTimer()
        clearSaveRetry()
        invalidatePendingSaves()
        draftProtected.value = true
        draftConflict.value = false
        draftDeleted.value = false
        lastSaveFailed.value = false
        removeLocalSnapshot()
    }

    const savePayload = async (payload: PostDraftData) => {
        requestController?.abort()
        const controller = new AbortController()
        requestController = controller
        try {
            return await saveDraftMutation.mutateAsync({
                ...payload,
                draftId: draftId.value ?? undefined,
                clientDraftKey: clientDraftKey.value,
                version: draftVersion.value ?? undefined,
                updatedAt: updatedAt.value ?? undefined,
            })
        } finally {
            if (requestController === controller) requestController = null
        }
    }

    const deleteDraft = async (targetDraftId: number) => {
        requestController?.abort()
        const controller = new AbortController()
        requestController = controller
        try {
            return await deleteDraftMutation.mutateAsync(targetDraftId)
        } finally {
            if (requestController === controller) requestController = null
        }
    }

    const persistNow = async () => {
        if (!options.enabled.value) return null
        const generation = sessionGeneration
        clearAutosaveTimer()
        const payload = options.buildPayload()
        const revision = localRevision
        const existingDraftId = draftId.value
        const shouldPersistToServer = hasMeaningfulDraftContent(payload)
            || (existingDraftId != null && payload.categoryId != null)
        if (!shouldPersistToServer) {
            if (existingDraftId != null) {
                try {
                    await deleteDraft(existingDraftId)
                } catch (error: unknown) {
                    logger.error('Failed to delete empty draft:', error)
                    throw error
                }
                if (options.ownerId?.value != null
                    && !markDraftDeletedLocally(options.ownerId.value, existingDraftId)) {
                    void reportDraftOperationalEvent('tombstone_write_failed')
                }
                removeLocalSnapshot()
            }
            if (generation !== sessionGeneration) return null
            resetDraftTracking()
            const storedLocally = storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), null, null))
            if (hasBrowserDraftContent(payload)) {
                if (!storedLocally) {
                    throw new Error('DRAFT_LOCAL_STORAGE_FAILED')
                }
                lastSavedAt.value = new Date().toISOString()
                lastSaveScope.value = 'browser'
            } else {
                lastSaveScope.value = null
            }
            lastSaveFailed.value = false
            clearSaveRetry()
            persistedRevision = localRevision
            options.onSaved?.()
            return null
        }

        storeLocalSnapshot(createDraftRecoverySnapshot(payload, draftId.value, updatedAt.value))
        const savedDraft = unwrapAxiosApiData(await savePayload(payload))
        options.onServerSaved?.(payload, savedDraft)
        if (generation !== sessionGeneration) return null
        draftId.value = savedDraft.draftId
        draftVersion.value = savedDraft.version ?? null
        clientDraftKey.value = savedDraft.clientDraftKey ?? clientDraftKey.value
        updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
        lastSavedAt.value = updatedAt.value
        lastSaveScope.value = 'server'
        staleReferencesReset.value = Boolean(savedDraft.staleReferencesReset)
        if (staleReferencesReset.value) {
            options.onServerReferencesReset?.(savedDraft)
            options.onStaleReferencesReset?.()
        }
        contractValidationFailed.value = false
        if (revision === localRevision) {
            persistedRevision = revision
            storeLocalSnapshot(createStoredSavedDraftSnapshot(payload, savedDraft, updatedAt.value))
            options.onSaved?.()
        } else {
            storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), savedDraft.draftId, updatedAt.value))
        }
        lastSaveFailed.value = false
        multipleDraftsFound.value = false
        clearSaveRetry()
        return savedDraft
    }

    const saveNow = async () => {
        if (draftConflict.value || draftProtected.value || draftDeleted.value || options.canPersist?.() === false) return null
        if (savePromise) {
            saveQueued = true
            return savePromise
        }
        const generation = sessionGeneration
        const pendingSave = (async () => {
            let savedDraft: DraftPost | null
            do {
                saveQueued = false
                savedDraft = await persistNow()
            } while (saveQueued && generation === sessionGeneration && options.enabled.value)
            return savedDraft
        })().catch((error: unknown) => {
            if (generation === sessionGeneration) {
                draftConflict.value = isDraftOutdatedError(error)
                draftProtected.value = isDraftProtectedError(error)
                if (draftConflict.value) void reportDraftOperationalEvent('draft_conflict')
                if (draftProtected.value) void reportDraftOperationalEvent('draft_protected')
                if (isDraftMissingError(error) && draftId.value != null) {
                    transitionToDeletedDraft()
                }
                lastSaveFailed.value = !draftConflict.value && !draftProtected.value && !draftDeleted.value
                if (draftConflict.value) clearAutosaveTimer()
                if (draftProtected.value) clearAutosaveTimer()
                if (lastSaveFailed.value && isTransientDraftSaveError(error)) {
                    scheduleTransientSaveRetry(error)
                } else {
                    clearSaveRetry()
                }
            }
            throw error
        })
        const trackedSave = pendingSave.finally(() => {
            if (savePromise === trackedSave) {
                savePromise = null
            }
        })
        savePromise = trackedSave
        return trackedSave
    }

    const isBrowserOnline = () => typeof navigator === 'undefined' || navigator.onLine

    const armSaveRetryTimer = () => {
        if (saveRetryDueAt == null) return
        const remainingDelayMs = Math.max(saveRetryDueAt - Date.now(), 0)
        saveRetryTimer = setTimeout(() => {
            saveRetryTimer = null
            if (!isBrowserOnline()) {
                saveRetryAttempt.value = Math.max(0, saveRetryAttempt.value - 1)
                return
            }
            if (saveRetryDueAt != null && saveRetryDueAt > Date.now()) {
                armSaveRetryTimer()
                return
            }
            saveRetryScheduled.value = false
            saveRetryDueAt = null
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to retry draft autosave:', error)
            })
        }, Math.min(remainingDelayMs, SAVE_RETRY_MAX_DELAY_MS))
    }

    function scheduleTransientSaveRetry(error?: unknown) {
        if (saveRetryTimer
            || saveRetryAttempt.value >= SAVE_RETRY_MAX_ATTEMPTS
            || !options.enabled.value
            || draftConflict.value
            || draftProtected.value
            || draftDeleted.value
            || options.canPersist?.() === false) {
            if (saveRetryAttempt.value >= SAVE_RETRY_MAX_ATTEMPTS && !saveRetryExhausted.value) {
                saveRetryExhausted.value = true
                logger.error('Draft autosave retries exhausted.', {
                    event: 'draft_autosave_retry_exhausted',
                    attempts: saveRetryAttempt.value,
                })
                void reportDraftOperationalEvent('autosave_retry_exhausted', {
                    attempts: saveRetryAttempt.value,
                })
            }
            return
        }

        if (saveRetryDueAt == null) {
            const retryAfterMs = getRetryAfterMs(error)
            const delayMs = retryAfterMs ?? getSaveRetryDelay(saveRetryAttempt.value + 1)
            saveRetryDueAt = Date.now() + delayMs
        }
        saveRetryScheduled.value = true
        if (!isBrowserOnline()) return

        saveRetryAttempt.value++
        armSaveRetryTimer()
    }

    const retrySaveNow = async () => {
        clearSaveRetry()
        return saveNow()
    }

    const scheduleAutosave = () => {
        if (!options.enabled.value || draftConflict.value || draftProtected.value || draftDeleted.value || options.canPersist?.() === false) return
        clearSaveRetry()
        clearAutosaveTimer()
        autosaveTimer = setTimeout(() => {
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to autosave draft:', error)
            })
        }, AUTOSAVE_DELAY_MS)
    }

    const reloadServerDraft = async () => {
        const generation = sessionGeneration
        const revision = localRevision
        const currentDraftId = draftId.value
        if (currentDraftId == null) return false
        isRestoringDraft.value = true
        try {
            const latestDraft = await loadDraftById(currentDraftId)
            if (generation !== sessionGeneration || draftId.value !== currentDraftId) return false
            if (revision !== localRevision) {
                draftConflict.value = true
                return false
            }
            if (!isMatchingLoadedDraft(latestDraft, options.buildPayload())) return false
            draftId.value = latestDraft.draftId
            draftVersion.value = latestDraft.version ?? null
            clientDraftKey.value = latestDraft.clientDraftKey ?? clientDraftKey.value
            updatedAt.value = getDraftUpdatedAt(latestDraft)
            lastSavedAt.value = updatedAt.value
            lastSaveScope.value = 'server'
            draftConflict.value = false
            draftProtected.value = false
            draftDeleted.value = false
            staleReferencesReset.value = false
            lastSaveFailed.value = false
            restoreFailed.value = false
            const serverSnapshot = latestDraft as unknown as DraftRecoverySnapshot
            const latestSnapshot = options.prepareRecoveredSnapshot?.(serverSnapshot) ?? serverSnapshot
            options.applyDraft(latestSnapshot)
            storeLocalSnapshot({
                ...latestSnapshot,
                draftId: latestDraft.draftId,
                updatedAt: updatedAt.value ?? undefined,
                clientModifiedAt: new Date().toISOString(),
                hasLocalChanges: false,
            })
            persistedRevision = localRevision
            options.onSaved?.()
            return true
        } finally {
            if (generation === sessionGeneration) isRestoringDraft.value = false
        }
    }

    const keepLocalDraft = async () => {
        const generation = sessionGeneration
        const currentDraftId = draftId.value
        if (currentDraftId == null) return false
        isRestoringDraft.value = true
        try {
            const latestDraft = await loadDraftById(currentDraftId)
            if (generation !== sessionGeneration || draftId.value !== currentDraftId) return false
            if (!isMatchingLoadedDraft(latestDraft, options.buildPayload())) return false
            updatedAt.value = getDraftUpdatedAt(latestDraft)
            draftVersion.value = latestDraft.version ?? null
            clientDraftKey.value = latestDraft.clientDraftKey ?? clientDraftKey.value
            draftConflict.value = false
            draftProtected.value = false
            draftDeleted.value = false
            staleReferencesReset.value = false
            await saveNow()
            return true
        } finally {
            if (generation === sessionGeneration) isRestoringDraft.value = false
        }
    }

    const restoreDraft = async () => {
        if (hasRestoredDraft.value || !options.enabled.value) return
        const generation = sessionGeneration
        const revision = localRevision
        hasRestoredDraft.value = true
        isRestoringDraft.value = true
        restoreFailed.value = false
        multipleDraftsFound.value = false

        try {
        cleanupExpiredDraftSnapshots()
        cleanupExpiredDraftTombstones()
        let localSnapshot = loadStoredDraftSnapshot(activeStorageKey.value)
        if (isDraftDeletedLocally(options.ownerId?.value, localSnapshot?.draftId)) {
            removeLocalSnapshot()
            localSnapshot = null
        }
        const preferredDraftId = isDraftDeletedLocally(options.ownerId?.value, options.preferredDraftId?.value)
            ? null
            : options.preferredDraftId?.value ?? null
        const payload = options.buildPayload()
        const resolved = await resolveServerDraftForRecovery({
            payload,
            localSnapshot,
            preferredDraftId,
            generationIsCurrent: () => generation === sessionGeneration,
            onStaleLocalSnapshot: (snapshot) => {
                const preparedSnapshot = options.prepareStaleSnapshot?.(snapshot) ?? snapshot
                resetDraftTracking()
                staleReferencesReset.value = true
                storeLocalSnapshot(preparedSnapshot)
                return preparedSnapshot
            },
        })
        if (generation !== sessionGeneration) return
        if (resolved.draftProtected) {
            transitionToProtectedDraft()
            return
        }

        const recovery = resolveDraftRecoverySnapshot(resolved.localSnapshot, resolved.serverDraft)
        const recoveredSnapshot = recovery.snapshot
        const chosen = recoveredSnapshot
            ? options.prepareRecoveredSnapshot?.(recoveredSnapshot) ?? recoveredSnapshot
            : null
        restoreFailed.value = resolved.recoveryFailed
        multipleDraftsFound.value = resolved.multipleMatchesFound
        if (multipleDraftsFound.value) void reportDraftOperationalEvent('multiple_recovery_candidates')
        if (!chosen) return

        if (revision !== localRevision) {
            if (resolved.serverDraft) {
                draftId.value = resolved.serverDraft.draftId
                draftVersion.value = resolved.serverDraft.version ?? null
                clientDraftKey.value = resolved.serverDraft.clientDraftKey ?? clientDraftKey.value
                draftConflict.value = true
                updatedAt.value = resolved.localSnapshot?.updatedAt
                    ?? resolved.localSnapshot?.modifiedAt
                    ?? null
            }
            restoreSource.value = 'local'
            storeLocalSnapshot(createDraftRecoverySnapshot(
                options.buildPayload(),
                draftId.value,
                updatedAt.value,
            ))
            return
        }

        draftId.value = recovery.conflict && resolved.serverDraft
            ? resolved.serverDraft.draftId
            : chosen.draftId ?? null
        draftVersion.value = recovery.conflict && resolved.serverDraft
            ? resolved.serverDraft.version ?? null
            : chosen.version ?? null
        clientDraftKey.value = chosen.clientDraftKey
            ?? resolved.serverDraft?.clientDraftKey
            ?? clientDraftKey.value
        // 충돌 중에는 로컬 변경이 갈라져 나온 기준 버전을 보존한다. 최신 서버
        // 버전은 사용자가 로컬본 덮어쓰기를 선택한 순간에만 다시 조회한다.
        updatedAt.value = chosen.updatedAt ?? chosen.modifiedAt ?? null
        draftConflict.value = recovery.conflict
        draftProtected.value = false
        draftDeleted.value = false
        staleReferencesReset.value = Boolean(chosen.staleReferencesReset)
        contractValidationFailed.value = Boolean(chosen.contractValidationFailed)
        restoreSource.value = recovery.source
        options.applyDraft(chosen)
        if (staleReferencesReset.value) options.onStaleReferencesReset?.()
        if (recovery.source === 'server') {
            storeLocalSnapshot({
                ...chosen,
                clientModifiedAt: new Date().toISOString(),
                hasLocalChanges: false,
            })
        } else {
            storeLocalSnapshot({
                ...chosen,
                draftId: draftId.value ?? undefined,
                clientModifiedAt: chosen.clientModifiedAt ?? new Date().toISOString(),
                hasLocalChanges: chosen.hasLocalChanges ?? true,
            })
        }
        } finally {
            if (generation === sessionGeneration) isRestoringDraft.value = false
        }
    }

    const retryRestore = async () => {
        hasRestoredDraft.value = false
        restoreFailed.value = false
        multipleDraftsFound.value = false
        await restoreDraft()
    }

    const clearRecovery = () => {
        invalidatePendingSaves()
        clearAutosaveTimer()
        clearSaveRetry()
        removeLocalSnapshot()
        lastSaveScope.value = null
        draftDeleted.value = false
        staleReferencesReset.value = false
        contractValidationFailed.value = false
        multipleDraftsFound.value = false
        resetDraftTracking()
        restoreSource.value = 'idle'
    }

    const resetSession = () => {
        invalidatePendingSaves()
        clearAutosaveTimer()
        clearSaveRetry()
        lastSaveScope.value = null
        lastSaveFailed.value = false
        lastLocalSaveFailed.value = false
        lastLocalRollbackFailed.value = false
        restoreFailed.value = false
        multipleDraftsFound.value = false
        isRestoringDraft.value = false
        draftConflict.value = false
        draftProtected.value = false
        draftDeleted.value = false
        staleReferencesReset.value = false
        contractValidationFailed.value = false
        resetDraftTracking()
        restoreSource.value = 'idle'
        hasRestoredDraft.value = false
    }

    watch(options.enabled, (enabled) => {
        if (!enabled) {
            clearAutosaveTimer()
            clearSaveRetry()
        }
    })

    const handleOnline = () => {
        if (!options.enabled.value || draftConflict.value || draftProtected.value || draftDeleted.value) return
        if (restoreFailed.value) {
            void retryRestore().catch((error: unknown) => {
                logger.error('Failed to retry draft recovery:', error)
            })
            return
        }
        if (lastSaveFailed.value && saveRetryScheduled.value && !saveRetryExhausted.value) {
            scheduleTransientSaveRetry()
        }
    }

    const handleOffline = () => {
        if (!saveRetryTimer) return
        clearTimeout(saveRetryTimer)
        saveRetryTimer = null
        saveRetryAttempt.value = Math.max(0, saveRetryAttempt.value - 1)
        saveRetryScheduled.value = true
    }

    const handleStorage = (event: StorageEvent) => {
        if (!options.enabled.value) return
        const ownerId = options.ownerId?.value
        if (draftId.value != null
            && ownerId != null
            && event.key === getDraftTombstoneKey(ownerId, draftId.value)
            && event.newValue) {
            transitionToDeletedDraft()
            return
        }
        if (event.key !== activeStorageKey.value) return
        if (!event.newValue) {
            // 만료·용량 정리·예약 발행 전환도 같은 localStorage 제거 이벤트를
            // 발생시킨다. 서버 삭제는 전용 tombstone으로만 판정한다.
            return
        }
        try {
            const incoming = parseDraftRecoverySnapshot(JSON.parse(event.newValue))
            if (!incoming) return
            if (!incoming.clientInstanceId || incoming.clientInstanceId === clientInstanceId) return
            const sameDraft = incoming.draftId != null && incoming.draftId === draftId.value
            const sameClientDraft = Boolean(incoming.clientDraftKey)
                && incoming.clientDraftKey === clientDraftKey.value
            const sameLogicalDraft = sameDraft || (draftId.value == null && sameClientDraft)
            const serverAdvanced = sameLogicalDraft
                && (incoming.updatedAt ?? incoming.modifiedAt ?? null) !== updatedAt.value
            const matchingComposer = isMatchingLoadedDraft(incoming as DraftPost, options.buildPayload())
            const hasUnsavedLocalChanges = localRevision !== persistedRevision
            if (!hasUnsavedLocalChanges
                && (draftId.value == null || sameDraft)
                && incoming.hasLocalChanges === true
                && matchingComposer) {
                draftId.value = incoming.draftId ?? null
                draftVersion.value = incoming.version ?? null
                clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
                updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
                localRevision++
                restoreSource.value = 'local'
                options.applyDraft(incoming)
                return
            }
            if (hasUnsavedLocalChanges
                && sameLogicalDraft
                && incoming.hasLocalChanges === false
                && matchingComposer
                && hasSameDraftContent(incoming, options.buildPayload())) {
                draftId.value = incoming.draftId ?? draftId.value
                draftVersion.value = incoming.version ?? null
                clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
                updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
                lastSavedAt.value = updatedAt.value
                lastSaveScope.value = 'server'
                persistedRevision = localRevision
                options.applyDraft(incoming)
                options.onSaved?.()
                return
            }
            if (!hasUnsavedLocalChanges
                && (draftId.value == null || sameDraft)
                && incoming.hasLocalChanges === false
                && matchingComposer
                && incoming.draftId != null) {
                draftId.value = incoming.draftId
                draftVersion.value = incoming.version ?? null
                clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
                updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
                lastSavedAt.value = updatedAt.value
                lastSaveScope.value = 'server'
                options.applyDraft(incoming)
                options.onSaved?.()
                return
            }
            if (hasUnsavedLocalChanges && (incoming.hasLocalChanges || serverAdvanced)) {
                draftConflict.value = true
                clearAutosaveTimer()
            }
        } catch (error: unknown) {
            logger.error('Failed to process a draft update from another tab:', error)
        }
    }

    const saveDeletedDraftAsNew = async () => {
        if (!draftDeleted.value) return false
        draftDeleted.value = false
        const saved = await saveNow()
        return saved != null || lastSaveScope.value === 'browser'
    }

    const discardDeletedDraft = () => {
        draftDeleted.value = false
        clearRecovery()
    }

    const unregisterDraftScheduledListener = typeof window !== 'undefined'
        ? registerDraftScheduledListener((scheduledEvent) => {
            if (!options.enabled.value || !matchesDraftScheduledEvent(
                scheduledEvent,
                options.ownerId?.value,
                draftId.value,
                clientDraftKey.value,
                activeStorageKey.value,
            )) return
            transitionToProtectedDraft()
            void reportDraftOperationalEvent('scheduled_in_another_tab')
        })
        : () => undefined

    const unregisterDraftDeletedListener = typeof window !== 'undefined'
        ? registerDraftDeletedListener((deletedEvent) => {
            if (!options.enabled.value
                || options.ownerId?.value == null
                || deletedEvent.ownerId !== String(options.ownerId.value)
                || draftId.value == null
                || deletedEvent.draftId !== String(draftId.value)) return
            transitionToDeletedDraft()
            void reportDraftOperationalEvent('deleted_in_another_tab')
        })
        : () => undefined

    if (typeof window !== 'undefined') {
        window.addEventListener('online', handleOnline)
        window.addEventListener('offline', handleOffline)
        window.addEventListener('storage', handleStorage)
    }

    const clearPublishedDraftRecovery = () => {
        const publishedDraftId = draftId.value
        if (publishedDraftId != null && options.ownerId?.value != null) {
            if (!markDraftDeletedLocally(options.ownerId.value, publishedDraftId)) {
                void reportDraftOperationalEvent('tombstone_write_failed')
            }
        }
        clearRecovery()
    }

    const clearScheduledDraftRecovery = (scheduledDraftId: number | null = draftId.value) => {
        if (options.ownerId?.value != null) {
            publishDraftScheduledEvent(
                options.ownerId.value,
                scheduledDraftId,
                clientDraftKey.value,
                activeStorageKey.value,
            )
        }
        clearRecovery()
    }

    onUnmounted(() => {
        clearAutosaveTimer()
        clearSaveRetry()
        invalidatePendingSaves()
        unregisterDraftScheduledListener()
        unregisterDraftDeletedListener()
        if (typeof window !== 'undefined') {
            window.removeEventListener('online', handleOnline)
            window.removeEventListener('offline', handleOffline)
            window.removeEventListener('storage', handleStorage)
        }
    })

    return {
        draftId,
        draftVersion: computed(() => draftVersion.value),
        clientDraftKey: computed(() => clientDraftKey.value),
        updatedAt,
        lastSavedAt: computed(() => lastSavedAt.value),
        lastSaveScope: computed(() => lastSaveScope.value),
        lastSaveFailed: computed(() => lastSaveFailed.value),
        saveRetryAttempt: computed(() => saveRetryAttempt.value),
        saveRetryScheduled: computed(() => saveRetryScheduled.value),
        saveRetryExhausted: computed(() => saveRetryExhausted.value),
        saveRetryMaxAttempts: SAVE_RETRY_MAX_ATTEMPTS,
        lastLocalSaveFailed: computed(() => lastLocalSaveFailed.value),
        restoreFailed: computed(() => restoreFailed.value),
        multipleDraftsFound: computed(() => multipleDraftsFound.value),
        isRestoringDraft: computed(() => isRestoringDraft.value),
        draftConflict: computed(() => draftConflict.value),
        draftProtected: computed(() => draftProtected.value),
        draftDeleted: computed(() => draftDeleted.value),
        staleReferencesReset: computed(() => staleReferencesReset.value),
        contractValidationFailed: computed(() => contractValidationFailed.value),
        isSavingDraft: computed(() => saveDraftMutation.isPending.value),
        restoreSource: computed(() => restoreSource.value),
        saveNow,
        retrySaveNow,
        scheduleAutosave,
        restoreDraft,
        retryRestore,
        reloadServerDraft,
        keepLocalDraft,
        resetSession,
        clearRecovery,
        clearPublishedDraftRecovery,
        clearScheduledDraftRecovery,
        writeLocalSnapshot,
        saveDeletedDraftAsNew,
        discardDeletedDraft,
    }
}
