import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import type { PostDraftData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { usePost } from '@/features/board/posts/queries/usePost'
import type { DraftPost } from '@/types'
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
    toIsoTime,
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
    parseDraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import {
    matchesDraftScheduledEvent,
    publishDraftScheduledEvent,
    registerDraftScheduledListener,
} from '@/features/board/posts/draft/postDraftScheduledEvent'
import {
    publishDraftUpdatedEvent,
    registerDraftUpdatedListener,
} from '@/features/board/posts/draft/postDraftUpdatedEvent'
import {
    createDraftSaveRetryController,
    SAVE_RETRY_MAX_ATTEMPTS,
} from '@/features/board/posts/draft/postDraftSaveRetry'
import { createDraftLocalSnapshotController } from '@/features/board/posts/draft/postDraftLocalSnapshot'

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
    onServerReferencesReset?: (savedDraft: DraftPost, payload: PostDraftData) => PostDraftData | void
    prepareStaleSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
    getDetachedDraftFileIdsToPreserve?: (payload: PostDraftData) => number[]
    onStaleReferencesReset?: () => void
    onLocalSnapshotStored?: (snapshot: DraftRecoverySnapshot) => void
    onLocalSnapshotAvailable?: (snapshot: DraftRecoverySnapshot) => void
    onLocalSnapshotRemoved?: () => void
    canPersist?: () => boolean
}

const AUTOSAVE_DELAY_MS = 1500

class DraftReferenceRecoveryLimitError extends Error {
    constructor() {
        super('Draft reference recovery retry limit exceeded')
        this.name = 'DraftReferenceRecoveryLimitError'
    }
}

export const isTransientDraftSaveError = (error: unknown) => {
    if (!isAxiosError(error)) return false
    const status = error.response?.status
    return status == null || status === 429 || status >= 500
}

export function usePostDraft(options: UsePostDraftOptions) {
    let requestController: AbortController | null = null
    let recoveryRequestController: AbortController | null = null
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
    const restoreFailed = ref(false)
    const multipleDraftsFound = ref(false)
    const isRestoringDraft = ref(false)
    const draftConflict = ref(false)
    const draftProtected = ref(false)
    const protectedDraftForkAvailable = ref(false)
    const draftDeleted = ref(false)
    const staleReferencesReset = ref(false)
    const contractValidationFailed = ref(false)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let savePromise: Promise<DraftPost | null> | null = null
    let saveQueued = false
    let localRevision = 0
    let persistedRevision = 0
    let lastRemoteLocalChangeAt = 0
    let sessionGeneration = 0
    const createClientKey = () => typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const clientInstanceId = createClientKey()
    const clientDraftKey = ref(createClientKey())
    const {
        activeStorageKey,
        lastSaveFailed: lastLocalSaveFailed,
        store: storeLocalSnapshot,
        load: loadLocalSnapshot,
        remove: removeLocalSnapshot,
        removeKey: removeLocalSnapshotByKey,
        resetStatus: resetLocalSnapshotStatus,
    } = createDraftLocalSnapshotController({
        storageKey: options.storageKey,
        resolveStorageKey: options.resolveStorageKey,
        draftId,
        draftVersion,
        clientDraftKey,
        clientInstanceId,
        getDetachedDraftFileIdsToPreserve: options.getDetachedDraftFileIdsToPreserve,
        onStored: options.onLocalSnapshotStored,
        onRemoved: options.onLocalSnapshotRemoved,
    })

    const {
        attempt: saveRetryAttempt,
        scheduled: saveRetryScheduled,
        exhausted: saveRetryExhausted,
        clear: clearSaveRetry,
        schedule: scheduleTransientSaveRetry,
        pauseForOffline: pauseSaveRetryForOffline,
    } = createDraftSaveRetryController({
        canRetry: () => options.enabled.value
            && !draftConflict.value
            && !draftProtected.value
            && !draftDeleted.value
            && options.canPersist?.() !== false,
        retry: () => saveNow(),
        onRetryError: (error) => {
            logger.error('Failed to retry draft autosave:', error)
        },
        onExhausted: (attempts) => {
            logger.error('Draft autosave retries exhausted.', {
                event: 'draft_autosave_retry_exhausted',
                attempts,
            })
            void reportDraftOperationalEvent('autosave_retry_exhausted', { attempts })
        },
    })

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
        lastRemoteLocalChangeAt = 0
    }

    const invalidatePendingSaves = (resetRevisions = true) => {
        sessionGeneration++
        requestController?.abort()
        requestController = null
        recoveryRequestController?.abort()
        recoveryRequestController = null
        savePromise = null
        saveQueued = false
        if (resetRevisions) {
            localRevision = 0
            persistedRevision = 0
            lastRemoteLocalChangeAt = 0
        }
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

    const startRecoveryRequest = () => {
        recoveryRequestController?.abort()
        const controller = new AbortController()
        recoveryRequestController = controller
        return controller
    }

    const finishRecoveryRequest = (controller: AbortController) => {
        if (recoveryRequestController !== controller) return false
        recoveryRequestController = null
        return true
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
        invalidatePendingSaves()
        resetDraftTracking()
        draftDeleted.value = true
        protectedDraftForkAvailable.value = false
        staleReferencesReset.value = true
        draftConflict.value = false
        draftProtected.value = false
        lastSaveFailed.value = false
        const currentPayload = options.buildPayload()
        const strippedSnapshot = stripDraftServerIdentity(
            createDraftRecoverySnapshot(currentPayload, null, null),
            options.getDetachedDraftFileIdsToPreserve?.(currentPayload),
        )
        const staleSnapshot = options.prepareStaleSnapshot?.(strippedSnapshot) ?? strippedSnapshot
        options.applyDraft(staleSnapshot)
        options.onStaleReferencesReset?.()
        const storedLocally = storeLocalSnapshot(staleSnapshot)
        if (!storedLocally) clearAutosaveTimer()
    }

    const transitionToProtectedDraft = () => {
        const localSnapshot = loadLocalSnapshot()
        const shouldPreserveLocalChanges = localRevision !== persistedRevision
            || localSnapshot?.hasLocalChanges === true
        clearAutosaveTimer()
        clearSaveRetry()
        invalidatePendingSaves()
        if (shouldPreserveLocalChanges) {
            removeLocalSnapshot()
            resetDraftTracking()
            const currentPayload = options.buildPayload()
            const strippedSnapshot = stripDraftServerIdentity({
                ...createDraftRecoverySnapshot(currentPayload, null, null),
                contractValidationFailed: options.canPersist?.() === false,
            }, options.getDetachedDraftFileIdsToPreserve?.(currentPayload))
            const staleSnapshot = options.prepareStaleSnapshot?.(strippedSnapshot) ?? strippedSnapshot
            options.applyDraft(staleSnapshot)
            options.onStaleReferencesReset?.()
            const storedLocally = storeLocalSnapshot(staleSnapshot)
            if (!storedLocally) clearAutosaveTimer()
        } else {
            removeLocalSnapshot()
        }
        protectedDraftForkAvailable.value = shouldPreserveLocalChanges
        draftProtected.value = true
        draftConflict.value = false
        draftDeleted.value = false
        staleReferencesReset.value = shouldPreserveLocalChanges
        lastSaveFailed.value = false
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
            return await deleteDraftMutation.mutateAsync({
                draftId: targetDraftId,
                version: draftVersion.value ?? undefined,
            })
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
                const deletedStorageKey = activeStorageKey.value
                try {
                    await deleteDraft(existingDraftId)
                } catch (error: unknown) {
                    logger.error('Failed to delete empty draft:', error)
                    throw error
                }
                if (generation !== sessionGeneration || !options.enabled.value) return null
                const latestPayload = options.buildPayload()
                if (revision !== localRevision || !hasSameDraftContent(payload, latestPayload)) {
                    resetDraftTracking()
                    const shouldStoreLatestPayload = hasBrowserDraftContent(latestPayload)
                    if (shouldStoreLatestPayload) {
                        const storedLocally = storeLocalSnapshot(createDraftRecoverySnapshot(
                            latestPayload,
                            null,
                            null,
                        ))
                        if (!storedLocally) throw new Error('DRAFT_LOCAL_STORAGE_FAILED')
                    } else {
                        removeLocalSnapshot()
                    }
                    if (deletedStorageKey !== activeStorageKey.value) {
                        removeLocalSnapshotByKey(deletedStorageKey)
                    }
                    if (options.ownerId?.value != null
                        && !markDraftDeletedLocally(options.ownerId.value, existingDraftId)) {
                        void reportDraftOperationalEvent('tombstone_write_failed')
                    }
                    if (hasMeaningfulDraftContent(latestPayload)) {
                        saveQueued = true
                        return null
                    }
                    lastSavedAt.value = shouldStoreLatestPayload
                        ? new Date().toISOString()
                        : null
                    lastSaveScope.value = shouldStoreLatestPayload ? 'browser' : null
                    lastSaveFailed.value = false
                    clearSaveRetry()
                    persistedRevision = localRevision
                    options.onSaved?.()
                    return null
                }
                if (options.ownerId?.value != null
                    && !markDraftDeletedLocally(options.ownerId.value, existingDraftId)) {
                    void reportDraftOperationalEvent('tombstone_write_failed')
                }
                removeLocalSnapshot()
            }
            if (generation !== sessionGeneration) return null
            resetDraftTracking()
            const latestPayload = options.buildPayload()
            if (hasBrowserDraftContent(latestPayload)) {
                const storedLocally = storeLocalSnapshot(createDraftRecoverySnapshot(latestPayload, null, null))
                if (!storedLocally) {
                    throw new Error('DRAFT_LOCAL_STORAGE_FAILED')
                }
                lastSavedAt.value = new Date().toISOString()
                lastSaveScope.value = 'browser'
            } else {
                removeLocalSnapshot()
                lastSavedAt.value = null
                lastSaveScope.value = null
            }
            lastSaveFailed.value = false
            clearSaveRetry()
            persistedRevision = localRevision
            options.onSaved?.()
            return null
        }

        storeLocalSnapshot(createDraftRecoverySnapshot(payload, draftId.value, updatedAt.value))
        let savedDraft = unwrapAxiosApiData(await savePayload(payload))
        if (generation !== sessionGeneration || !options.enabled.value) return null
        let canonicalPayload = payload
        let referenceRecoverySaveCount = 0
        while (true) {
            options.onServerSaved?.(canonicalPayload, savedDraft)
            draftId.value = savedDraft.draftId
            draftVersion.value = savedDraft.version ?? null
            clientDraftKey.value = savedDraft.clientDraftKey ?? clientDraftKey.value
            updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
            lastSavedAt.value = updatedAt.value
            lastSaveScope.value = 'server'
            lastRemoteLocalChangeAt = 0
            staleReferencesReset.value = Boolean(savedDraft.staleReferencesReset)
            if (!staleReferencesReset.value) break

            const recoveredPayload = options.onServerReferencesReset?.(savedDraft, canonicalPayload)
            options.onStaleReferencesReset?.()
            if (!recoveredPayload) break
            canonicalPayload = recoveredPayload
            if (hasSameDraftContent(recoveredPayload, savedDraft)) break
            storeLocalSnapshot(createDraftRecoverySnapshot(
                recoveredPayload,
                savedDraft.draftId,
                updatedAt.value,
            ))
            if (referenceRecoverySaveCount >= 2) {
                throw new DraftReferenceRecoveryLimitError()
            }
            referenceRecoverySaveCount++
            savedDraft = unwrapAxiosApiData(await savePayload(recoveredPayload))
            if (generation !== sessionGeneration || !options.enabled.value) return null
        }
        const latestPayload = options.buildPayload()
        const hasNewerLocalChanges = revision !== localRevision
            || !hasSameDraftContent(canonicalPayload, latestPayload)
        contractValidationFailed.value = hasNewerLocalChanges
            && options.canPersist?.() === false
        const canonicalSnapshot = {
            ...createStoredSavedDraftSnapshot(canonicalPayload, savedDraft, updatedAt.value),
            clientInstanceId,
        }
        if (!hasNewerLocalChanges) {
            persistedRevision = revision
            storeLocalSnapshot(canonicalSnapshot)
            options.onSaved?.()
        } else {
            storeLocalSnapshot({
                ...createDraftRecoverySnapshot(latestPayload, savedDraft.draftId, updatedAt.value),
                contractValidationFailed: contractValidationFailed.value,
            })
        }
        if (options.ownerId?.value != null) {
            publishDraftUpdatedEvent(options.ownerId.value, activeStorageKey.value, canonicalSnapshot)
        }
        lastSaveFailed.value = false
        multipleDraftsFound.value = false
        clearSaveRetry()
        return savedDraft
    }

    async function saveNow() {
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
            if (generation !== sessionGeneration || !options.enabled.value) return null
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
                const referenceRecoveryLimitReached = error instanceof DraftReferenceRecoveryLimitError
                if (referenceRecoveryLimitReached && saveRetryAttempt.value === 0) {
                    void reportDraftOperationalEvent('reference_recovery_retry_scheduled', {
                        maxImmediateRetries: 2,
                    })
                }
                if (lastSaveFailed.value
                    && (isTransientDraftSaveError(error) || referenceRecoveryLimitReached)) {
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
        const controller = startRecoveryRequest()
        isRestoringDraft.value = true
        try {
            const latestDraft = await loadDraftById(currentDraftId, {
                signal: controller.signal,
                skipGlobalErrorHandler: true,
            })
            if (generation !== sessionGeneration
                || recoveryRequestController !== controller
                || draftId.value !== currentDraftId) return false
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
            const preparedSnapshotChanged = !hasSameDraftContent(serverSnapshot, latestSnapshot)
            staleReferencesReset.value = Boolean(latestSnapshot.staleReferencesReset) || preparedSnapshotChanged
            options.applyDraft(latestSnapshot)
            storeLocalSnapshot({
                ...latestSnapshot,
                draftId: latestDraft.draftId,
                updatedAt: updatedAt.value ?? undefined,
                clientModifiedAt: new Date().toISOString(),
                hasLocalChanges: preparedSnapshotChanged,
            })
            if (preparedSnapshotChanged) {
                localRevision++
                options.onStaleReferencesReset?.()
                scheduleAutosave()
            } else {
                persistedRevision = localRevision
                options.onSaved?.()
            }
            return true
        } finally {
            if (finishRecoveryRequest(controller) && generation === sessionGeneration) {
                isRestoringDraft.value = false
            }
        }
    }

    const keepLocalDraft = async () => {
        const generation = sessionGeneration
        const currentDraftId = draftId.value
        if (currentDraftId == null) return false
        const controller = startRecoveryRequest()
        isRestoringDraft.value = true
        try {
            const latestDraft = await loadDraftById(currentDraftId, {
                signal: controller.signal,
                skipGlobalErrorHandler: true,
            })
            if (generation !== sessionGeneration
                || recoveryRequestController !== controller
                || draftId.value !== currentDraftId) return false
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
            if (finishRecoveryRequest(controller) && generation === sessionGeneration) {
                isRestoringDraft.value = false
            }
        }
    }

    const restoreDraft = async () => {
        if (hasRestoredDraft.value || !options.enabled.value) return
        const generation = sessionGeneration
        const revision = localRevision
        const controller = startRecoveryRequest()
        hasRestoredDraft.value = true
        isRestoringDraft.value = true
        restoreFailed.value = false
        multipleDraftsFound.value = false

        try {
        cleanupExpiredDraftSnapshots()
        cleanupExpiredDraftTombstones()
        let localSnapshot = loadLocalSnapshot()
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
            signal: controller.signal,
            generationIsCurrent: () => generation === sessionGeneration
                && recoveryRequestController === controller,
            onStaleLocalSnapshot: (snapshot) => {
                const preparedSnapshot = options.prepareStaleSnapshot?.(snapshot) ?? snapshot
                resetDraftTracking()
                staleReferencesReset.value = true
                storeLocalSnapshot(preparedSnapshot)
                return preparedSnapshot
            },
        })
        if (generation !== sessionGeneration || recoveryRequestController !== controller) return
        if (resolved.draftProtected) {
            transitionToProtectedDraft()
            return
        }

        const recovery = resolveDraftRecoverySnapshot(resolved.localSnapshot, resolved.serverDraft)
        if (resolved.localSnapshot) options.onLocalSnapshotAvailable?.(resolved.localSnapshot)
        const recoveredSnapshot = recovery.snapshot
        const chosen = recoveredSnapshot
            ? options.prepareRecoveredSnapshot?.(recoveredSnapshot) ?? recoveredSnapshot
            : null
        const preparedServerSnapshotChanged = recovery.source === 'server'
            && resolved.serverDraft != null
            && chosen != null
            && !hasSameDraftContent(chosen, resolved.serverDraft as unknown as PostDraftData)
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
                hasLocalChanges: preparedServerSnapshotChanged,
            })
            if (preparedServerSnapshotChanged) {
                localRevision++
                scheduleAutosave()
            }
        } else {
            storeLocalSnapshot({
                ...chosen,
                draftId: draftId.value ?? undefined,
                clientModifiedAt: chosen.clientModifiedAt ?? new Date().toISOString(),
                hasLocalChanges: chosen.hasLocalChanges ?? true,
            })
        }
        } finally {
            if (finishRecoveryRequest(controller) && generation === sessionGeneration) {
                isRestoringDraft.value = false
            }
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
        protectedDraftForkAvailable.value = false
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
        resetLocalSnapshotStatus()
        restoreFailed.value = false
        multipleDraftsFound.value = false
        isRestoringDraft.value = false
        draftConflict.value = false
        draftProtected.value = false
        protectedDraftForkAvailable.value = false
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
            invalidatePendingSaves(false)
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
        pauseSaveRetryForOffline()
    }

    const reconcileIncomingSnapshot = (incoming: DraftRecoverySnapshot) => {
        if (!incoming.clientInstanceId || incoming.clientInstanceId === clientInstanceId) return
        const sameDraft = incoming.draftId != null && incoming.draftId === draftId.value
        const sameClientDraft = Boolean(incoming.clientDraftKey)
            && incoming.clientDraftKey === clientDraftKey.value
        const sameLogicalDraft = sameDraft || (draftId.value == null && sameClientDraft)
        const incomingServerTime = toIsoTime(incoming.updatedAt ?? incoming.modifiedAt)
        const currentServerTime = toIsoTime(updatedAt.value)
        const serverRevisionOrder = incoming.version != null && draftVersion.value != null
            ? Math.sign(incoming.version - draftVersion.value)
            : incomingServerTime && currentServerTime
                ? Math.sign(Date.parse(incomingServerTime) - Date.parse(currentServerTime))
                : null
        if (sameLogicalDraft && incoming.hasLocalChanges === false && serverRevisionOrder != null) {
            if (serverRevisionOrder < 0) return
            if (serverRevisionOrder === 0
                && draftId.value != null
                && !hasSameDraftContent(incoming, options.buildPayload())) return
        }
        const incomingClientModifiedAt = toIsoTime(incoming.clientModifiedAt)
        const incomingClientModifiedAtMs = incomingClientModifiedAt
            ? Date.parse(incomingClientModifiedAt)
            : 0
        if (sameLogicalDraft
            && incoming.hasLocalChanges === true
            && lastRemoteLocalChangeAt > 0
            && incomingClientModifiedAtMs <= lastRemoteLocalChangeAt) return
        const serverAdvanced = sameLogicalDraft && serverRevisionOrder != null && serverRevisionOrder > 0
        const matchingComposer = isMatchingLoadedDraft(incoming as DraftPost, options.buildPayload())
        const hasUnsavedLocalChanges = localRevision !== persistedRevision
        if (!hasUnsavedLocalChanges
            && sameLogicalDraft
            && incoming.hasLocalChanges === true
            && matchingComposer) {
            draftId.value = incoming.draftId ?? null
            draftVersion.value = incoming.version ?? null
            clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
            updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
            localRevision++
            lastRemoteLocalChangeAt = incomingClientModifiedAtMs
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
            lastRemoteLocalChangeAt = 0
            options.applyDraft(incoming)
            options.onSaved?.()
            return
        }
        if (!hasUnsavedLocalChanges
            && sameLogicalDraft
            && incoming.hasLocalChanges === false
            && matchingComposer
            && incoming.draftId != null) {
            draftId.value = incoming.draftId
            draftVersion.value = incoming.version ?? null
            clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
            updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
            lastSavedAt.value = updatedAt.value
            lastSaveScope.value = 'server'
            lastRemoteLocalChangeAt = 0
            options.applyDraft(incoming)
            options.onSaved?.()
            return
        }
        if (hasUnsavedLocalChanges
            && sameLogicalDraft
            && (incoming.hasLocalChanges || serverAdvanced)) {
            draftConflict.value = true
            clearAutosaveTimer()
        }
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
            options.onLocalSnapshotAvailable?.(incoming)
            reconcileIncomingSnapshot(incoming)
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

    const saveProtectedDraftAsNew = async () => {
        if (!draftProtected.value || !protectedDraftForkAvailable.value) return false
        draftProtected.value = false
        protectedDraftForkAvailable.value = false
        const saved = await saveNow()
        return saved != null || lastSaveScope.value === 'browser'
    }

    const discardProtectedDraftFork = () => {
        if (!protectedDraftForkAvailable.value) return
        protectedDraftForkAvailable.value = false
        draftProtected.value = false
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

    const unregisterDraftUpdatedListener = typeof window !== 'undefined'
        ? registerDraftUpdatedListener((updatedEvent) => {
            if (!options.enabled.value
                || options.ownerId?.value == null
                || updatedEvent.ownerId !== String(options.ownerId.value)) return
            reconcileIncomingSnapshot(updatedEvent.snapshot)
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
        unregisterDraftUpdatedListener()
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
        protectedDraftForkAvailable: computed(() => protectedDraftForkAvailable.value),
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
        saveProtectedDraftAsNew,
        discardProtectedDraftFork,
    }
}
