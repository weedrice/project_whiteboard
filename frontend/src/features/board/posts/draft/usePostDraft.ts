import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import type { PostDraftData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { usePost } from '@/features/board/posts/queries/usePost'
import type { DraftPost } from '@/types'
import logger from '@/utils/logger'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import {
    createDraftContentFingerprint,
    getDraftUpdatedAt,
    hasSameDraftContent,
    isDraftMissingError,
    isDraftProtectedError,
    isDraftOutdatedError,
    loadDraftById,
} from '@/features/board/posts/draft/postDraftRecovery'
import {
    createDraftRecoverySnapshot,
    createStoredSavedDraftSnapshot,
    hasBrowserDraftContent,
    hasMeaningfulDraftContent,
} from '@/features/board/posts/draft/postDraftSnapshot'
import {
    getDraftTombstoneKey,
    markDraftDeletedLocally,
    registerDraftDeletedListener,
} from '@/features/board/posts/draft/postDraftTombstone'
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
import { createDraftStateTransitionController } from '@/features/board/posts/draft/postDraftStateTransitions'
import { createDraftCrossTabReconciler } from '@/features/board/posts/draft/postDraftCrossTabReconciler'
import { createDraftRecoveryCoordinator } from '@/features/board/posts/draft/postDraftRecoveryCoordinator'
import { createDraftSessionStatusController } from '@/features/board/posts/draft/postDraftStatus'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import type {
    DraftContentAdapter,
    DraftLifecycleEvent,
    DraftSaveResult,
} from '@/features/board/posts/draft/postDraftContracts'

export type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

interface UsePostDraftOptions {
    enabled: Ref<boolean>
    storageKey: Ref<string>
    resolveStorageKey?: (draftId: number) => string
    ownerId?: Ref<string | number | null | undefined>
    preferredDraftId?: Ref<number | null>
    content: DraftContentAdapter
    onEvent: (event: DraftLifecycleEvent) => void
}

const AUTOSAVE_DELAY_MS = 1500

class DraftReferenceRecoveryLimitError extends Error {
    constructor() {
        super('Draft reference recovery retry limit exceeded')
        this.name = 'DraftReferenceRecoveryLimitError'
    }
}

const isTransientDraftSaveError = (error: unknown) => {
    if (!isAxiosError(error)) return false
    const status = error.response?.status
    return status == null || status === 429 || status >= 500
}

export function usePostDraft(options: UsePostDraftOptions) {
    const createClientKey = () => typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const {
        availability,
        operation,
        persistence,
        draftConflict,
        draftProtected,
        draftDeleted,
        protectedDraftForkAvailable,
        isRestoringDraft,
        isSavingDraft,
        restoreFailed,
        lastSaveFailed,
        lastSavedAt,
        lastSaveScope,
        setRetryWait,
        setPersistenceFailure,
        identity: { draftId, clientDraftKey, version: draftVersion, updatedAt },
        getGeneration,
        getLocalRevision,
        incrementLocalRevision,
        markCurrentRevisionPersisted,
        setPersistedRevision,
        getPersistedRevision,
        getLastRemoteLocalChangeAt,
        setLastRemoteLocalChangeAt,
        startRequest,
        finishRequest,
        isRequestCurrent,
        getRequestSignal,
        invalidatePendingWork,
        resetIdentity,
        reset: resetSessionStatus,
    } = createDraftSessionStatusController('active', createClientKey)
    const { useSaveDraft, useDeleteDraft } = usePost()
    if (typeof useSaveDraft !== 'function' || typeof useDeleteDraft !== 'function') {
        throw new Error('Draft mutations are not available.')
    }
    const resolveRequestConfig = () => {
        const signal = getRequestSignal('save')
        return signal ? { signal, skipGlobalErrorHandler: true } : undefined
    }
    const saveDraftMutation = useSaveDraft(resolveRequestConfig)
    const deleteDraftMutation = useDeleteDraft(resolveRequestConfig)
    const staleReferencesReset = ref(false)
    const contractValidationFailed = ref(false)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let savePromise: Promise<DraftPost | null> | null = null
    let saveQueued = false
    const clientInstanceId = createClientKey()
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
        getDetachedDraftFileIdsToPreserve: options.content.selectDetachedUploadIds,
        onStored: (snapshot) => options.onEvent({ type: 'local-snapshot-stored', snapshot }),
        onRemoved: () => options.onEvent({ type: 'local-snapshot-deleted' }),
    })
    watch(lastLocalSaveFailed, (failed) => {
        if (failed) setPersistenceFailure('browser')
        else if (persistence.value.type === 'failed' && persistence.value.target === 'browser') {
            lastSaveFailed.value = false
        }
    }, { flush: 'sync' })

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
            && options.content.canPersist(),
        retry: () => saveNowInternal(),
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
        resetIdentity()
        lastSavedAt.value = null
    }

    const invalidatePendingSaves = (resetRevisions = true) => {
        invalidatePendingWork(resetRevisions, () => {
            savePromise = null
            saveQueued = false
        })
    }

    const {
        transitionToDeletedDraft,
        transitionToProtectedDraft: applyProtectedDraftTransition,
    } = createDraftStateTransitionController({
        session: {
            draftId,
            ownerId: options.ownerId,
            draftDeleted,
            draftProtected,
            protectedDraftForkAvailable,
            staleReferencesReset,
            draftConflict,
            lastSaveFailed,
            localRevision: getLocalRevision,
            persistedRevision: getPersistedRevision,
            clearAutosaveTimer,
            clearSaveRetry,
            invalidatePendingSaves,
            resetDraftTracking,
        },
        content: options.content,
        localStore: {
            load: loadLocalSnapshot,
            remove: removeLocalSnapshot,
            store: storeLocalSnapshot,
        },
        onEvent: options.onEvent,
    })
    const transitionToProtectedDraft = () => {
        applyProtectedDraftTransition(!options.content.canPersist())
    }

    const { reconcile: reconcileIncomingSnapshot } = createDraftCrossTabReconciler({
        clientInstanceId,
        draftId,
        draftVersion,
        clientDraftKey,
        updatedAt,
        lastSavedAt,
        lastSaveScope,
        draftConflict,
        buildPayload: options.content.buildPayload,
        onSaved: () => options.onEvent({
            type: 'saved',
            scope: lastSaveScope.value ?? 'browser',
        }),
        clearAutosaveTimer,
        getLocalRevision,
        getPersistedRevision,
        markCurrentRevisionPersisted,
        getLastRemoteLocalChangeAt,
        setLastRemoteLocalChangeAt,
    })

    const writeLocalSnapshot = () => {
        if (!options.enabled.value) return
        incrementLocalRevision()
        contractValidationFailed.value = !options.content.canPersist()
        const snapshot = {
            ...createDraftRecoverySnapshot(options.content.buildPayload(), draftId.value, updatedAt.value),
            contractValidationFailed: contractValidationFailed.value,
        }
        return storeLocalSnapshot(snapshot)
    }

    const startRecoveryRequest = () => {
        return startRequest('recovery')
    }

    const finishRecoveryRequest = (controller: AbortController) => {
        return finishRequest('recovery', controller)
    }

    const savePayload = async (payload: PostDraftData) => {
        const controller = startRequest('save')
        try {
            return await saveDraftMutation.mutateAsync({
                ...payload,
                draftId: draftId.value ?? undefined,
                clientDraftKey: clientDraftKey.value,
                version: draftVersion.value ?? undefined,
                updatedAt: updatedAt.value ?? undefined,
            })
        } finally {
            finishRequest('save', controller)
        }
    }

    const deleteDraft = async (targetDraftId: number) => {
        const controller = startRequest('save')
        try {
            return await deleteDraftMutation.mutateAsync({
                draftId: targetDraftId,
                version: draftVersion.value ?? undefined,
            })
        } finally {
            finishRequest('save', controller)
        }
    }

    const persistNow = async () => {
        if (!options.enabled.value) return null
        const generation = getGeneration()
        clearAutosaveTimer()
        const payload = options.content.buildPayload()
        const revision = getLocalRevision()
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
                if (generation !== getGeneration() || !options.enabled.value) return null
                const latestPayload = options.content.buildPayload()
                if (revision !== getLocalRevision() || !hasSameDraftContent(payload, latestPayload)) {
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
                    markCurrentRevisionPersisted()
                    options.onEvent({ type: 'saved', scope: lastSaveScope.value ?? 'browser' })
                    return null
                }
                if (options.ownerId?.value != null
                    && !markDraftDeletedLocally(options.ownerId.value, existingDraftId)) {
                    void reportDraftOperationalEvent('tombstone_write_failed')
                }
                removeLocalSnapshot()
            }
            if (generation !== getGeneration()) return null
            resetDraftTracking()
            const latestPayload = options.content.buildPayload()
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
            markCurrentRevisionPersisted()
            options.onEvent({ type: 'saved', scope: lastSaveScope.value ?? 'browser' })
            return null
        }

        storeLocalSnapshot(createDraftRecoverySnapshot(payload, draftId.value, updatedAt.value))
        let savedDraft = unwrapAxiosApiData(await savePayload(payload))
        if (generation !== getGeneration() || !options.enabled.value) return null
        let canonicalPayload = payload
        let referenceRecoverySaveCount = 0
        while (true) {
            options.onEvent({ type: 'server-saved', payload: canonicalPayload, draft: savedDraft })
            if ((savedDraft.evictedDraftCount ?? 0) > 0) {
                options.onEvent({ type: 'limit-evicted', count: savedDraft.evictedDraftCount! })
            }
            draftId.value = savedDraft.draftId
            draftVersion.value = savedDraft.version ?? null
            clientDraftKey.value = savedDraft.clientDraftKey ?? clientDraftKey.value
            updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
            lastSavedAt.value = updatedAt.value
            lastSaveScope.value = 'server'
            setLastRemoteLocalChangeAt(0)
            staleReferencesReset.value = Boolean(savedDraft.staleReferencesReset)
            if (!staleReferencesReset.value) break

            const recoveredPayload = options.content.recoverServerReferences(savedDraft, canonicalPayload)
            options.onEvent({ type: 'references-removed' })
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
            if (generation !== getGeneration() || !options.enabled.value) return null
        }
        const latestPayload = options.content.buildPayload()
        const hasNewerLocalChanges = revision !== getLocalRevision()
            || !hasSameDraftContent(canonicalPayload, latestPayload)
        contractValidationFailed.value = hasNewerLocalChanges
            && !options.content.canPersist()
        const canonicalSnapshot = {
            ...createStoredSavedDraftSnapshot(canonicalPayload, savedDraft, updatedAt.value),
            clientInstanceId,
        }
        if (!hasNewerLocalChanges) {
            setPersistedRevision(revision)
            storeLocalSnapshot(canonicalSnapshot)
            options.onEvent({ type: 'saved', scope: 'server' })
        } else {
            storeLocalSnapshot({
                ...createDraftRecoverySnapshot(latestPayload, savedDraft.draftId, updatedAt.value),
                contractValidationFailed: contractValidationFailed.value,
            })
        }
        if (options.ownerId?.value != null) {
            publishDraftUpdatedEvent(options.ownerId.value, {
                draftId: savedDraft.draftId,
                clientDraftKey: savedDraft.clientDraftKey ?? clientDraftKey.value,
                version: savedDraft.version ?? null,
                updatedAt: updatedAt.value ?? new Date().toISOString(),
                contentFingerprint: createDraftContentFingerprint(canonicalPayload),
            })
        }
        lastSaveFailed.value = false
        clearSaveRetry()
        return savedDraft
    }

    async function saveNowInternal() {
        clearAutosaveTimer()
        if (draftConflict.value || draftProtected.value || draftDeleted.value || !options.content.canPersist()) return null
        if (savePromise) {
            saveQueued = true
            return savePromise
        }
        const generation = getGeneration()
        isSavingDraft.value = true
        const pendingSave = (async () => {
            let savedDraft: DraftPost | null
            let savePass = 0
            do {
                saveQueued = false
                savedDraft = await persistNow()
                savePass++
            } while (saveQueued
                && savePass < 2
                && generation === getGeneration()
                && options.enabled.value)
            if (saveQueued && generation === getGeneration() && options.enabled.value) {
                scheduleAutosave()
            }
            return savedDraft
        })().catch((error: unknown) => {
            if (generation !== getGeneration() || !options.enabled.value) return null
            if (generation === getGeneration()) {
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
                    setRetryWait(saveRetryAttempt.value, SAVE_RETRY_MAX_ATTEMPTS)
                } else {
                    clearSaveRetry()
                }
            }
            throw error
        })
        const trackedSave = pendingSave.finally(() => {
            if (savePromise === trackedSave) {
                savePromise = null
                if (operation.value.type === 'saving') isSavingDraft.value = false
            }
        })
        savePromise = trackedSave
        return trackedSave
    }

    const toSaveResult = (savedDraft: DraftPost | null): DraftSaveResult => {
        if (savedDraft) return { type: 'server', draft: savedDraft }
        if (lastSaveScope.value === 'browser') return { type: 'browser' }
        if (draftId.value == null && !hasBrowserDraftContent(options.content.buildPayload())) {
            return { type: 'cleared' }
        }
        return { type: 'skipped' }
    }

    const saveNow = async () => toSaveResult(await saveNowInternal())

    const retrySaveNow = async () => {
        clearSaveRetry()
        return toSaveResult(await saveNowInternal())
    }

    const scheduleAutosave = () => {
        if (!options.enabled.value || draftConflict.value || draftProtected.value || draftDeleted.value || !options.content.canPersist()) return
        clearSaveRetry()
        clearAutosaveTimer()
        autosaveTimer = setTimeout(() => {
            void saveNowInternal().catch((error: unknown) => {
                logger.error('Failed to autosave draft:', error)
            })
        }, AUTOSAVE_DELAY_MS)
    }

    const {
        reloadServerDraft,
        keepLocalDraft,
        restoreDraft,
        retryRestore,
    } = createDraftRecoveryCoordinator({
        session: {
            enabled: options.enabled,
            ownerId: options.ownerId,
            preferredDraftId: options.preferredDraftId,
            draftId,
            draftVersion,
            clientDraftKey,
            updatedAt,
            lastSavedAt,
            lastSaveScope,
            lastSaveFailed,
            restoreFailed,
            isRestoringDraft,
            draftConflict,
            draftProtected,
            draftDeleted,
            staleReferencesReset,
            contractValidationFailed,
            restoreSource,
            hasRestoredDraft,
            getGeneration,
            getLocalRevision,
            incrementLocalRevision,
            markCurrentRevisionPersisted,
            startRecoveryRequest,
            finishRecoveryRequest,
            isRecoveryRequestCurrent: (controller) => isRequestCurrent('recovery', controller),
            resetDraftTracking,
        },
        remote: {
            loadById: loadDraftById,
            resolve: resolveServerDraftForRecovery,
        },
        localStore: {
            load: loadLocalSnapshot,
            remove: removeLocalSnapshot,
            store: storeLocalSnapshot,
        },
        content: options.content,
        workflow: {
            transitionToProtectedDraft,
            scheduleAutosave,
            saveNow: saveNowInternal,
        },
        onEvent: options.onEvent,
    })

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
        resetSessionStatus()
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

    const handleStorage = (event: StorageEvent) => {
        if (!options.enabled.value || !event.newValue) return
        const ownerId = options.ownerId?.value
        if (draftId.value != null
            && ownerId != null
            && event.key === getDraftTombstoneKey(ownerId, draftId.value)) {
            transitionToDeletedDraft()
        }
    }

    const saveDeletedDraftAsNew = async () => {
        if (!draftDeleted.value) return false
        draftDeleted.value = false
        const saved = await saveNowInternal()
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
        const saved = await saveNowInternal()
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
            reconcileIncomingSnapshot({ ...updatedEvent, hasLocalChanges: false })
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
        lastSavedAt: computed(() => lastSavedAt.value),
        lastSaveScope: computed(() => lastSaveScope.value),
        lastSaveFailed: computed(() => lastSaveFailed.value),
        saveRetryAttempt: computed(() => saveRetryAttempt.value),
        saveRetryScheduled: computed(() => saveRetryScheduled.value),
        saveRetryExhausted: computed(() => saveRetryExhausted.value),
        saveRetryMaxAttempts: SAVE_RETRY_MAX_ATTEMPTS,
        lastLocalSaveFailed: computed(() => lastLocalSaveFailed.value),
        restoreFailed: computed(() => restoreFailed.value),
        isRestoringDraft: computed(() => isRestoringDraft.value),
        draftConflict: computed(() => draftConflict.value),
        draftProtected: computed(() => draftProtected.value),
        protectedDraftForkAvailable: computed(() => protectedDraftForkAvailable.value),
        draftDeleted: computed(() => draftDeleted.value),
        availability: computed(() => availability.value),
        operation: computed(() => operation.value),
        persistence: computed(() => persistence.value),
        contractValidationFailed: computed(() => contractValidationFailed.value),
        isSavingDraft: computed(() => isSavingDraft.value),
        restoreSource: computed(() => restoreSource.value),
        saveNow,
        retrySaveNow,
        scheduleAutosave,
        restoreDraft,
        retryRestore,
        reloadServerDraft,
        keepLocalDraft,
        resetSession,
        clearPublishedDraftRecovery,
        clearScheduledDraftRecovery,
        writeLocalSnapshot,
        saveDeletedDraftAsNew,
        discardDeletedDraft,
        saveProtectedDraftAsNew,
        discardProtectedDraftFork,
    }
}
