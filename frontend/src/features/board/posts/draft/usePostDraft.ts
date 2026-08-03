import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import type { PostDraftData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { usePost } from '@/features/board/posts/queries/usePost'
import type { DraftPost } from '@/types'
import { Storage } from '@/utils/storage'
import logger from '@/utils/logger'
import {
    getDraftUpdatedAt,
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
} from '@/features/board/posts/draft/postDraftSnapshot'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import {
    cleanupExpiredDraftTombstones,
    getDraftTombstoneKey,
    isDraftDeletedLocally,
    markDraftDeletedLocally,
} from '@/features/board/posts/draft/postDraftTombstone'
import {
    cleanupExpiredDraftSnapshots,
    loadStoredDraftSnapshot,
    storeDraftSnapshotWithBudget,
} from '@/features/board/posts/draft/postDraftLifecycle'

export type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
export type DraftSaveScope = 'server' | 'browser'

interface UsePostDraftOptions {
    enabled: Ref<boolean>
    storageKey: Ref<string>
    ownerId?: Ref<string | number | null | undefined>
    preferredDraftId?: Ref<number | null>
    buildPayload: () => PostDraftData
    applyDraft: (draft: DraftRecoverySnapshot) => void
    onSaved?: () => void
    onServerSaved?: (payload: PostDraftData) => void
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
    const restoreFailed = ref(false)
    const isRestoringDraft = ref(false)
    const draftConflict = ref(false)
    const draftProtected = ref(false)
    const draftDeleted = ref(false)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let saveRetryTimer: ReturnType<typeof setTimeout> | null = null
    let saveRetryAttempt = 0
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
        const stored = storeDraftSnapshotWithBudget(options.storageKey.value, {
            ...snapshot,
            clientDraftKey: snapshot.clientDraftKey ?? clientDraftKey.value,
            version: snapshot.version ?? draftVersion.value ?? undefined,
            clientInstanceId,
        })
        lastLocalSaveFailed.value = !stored
        return stored
    }

    const writeLocalSnapshot = () => {
        if (!options.enabled.value) return
        localRevision++
        const snapshot = createDraftRecoverySnapshot(options.buildPayload(), draftId.value, updatedAt.value)
        return storeLocalSnapshot(snapshot)
    }

    const clearSaveRetry = (resetAttempt = true) => {
        if (saveRetryTimer) {
            clearTimeout(saveRetryTimer)
            saveRetryTimer = null
        }
        if (resetAttempt) saveRetryAttempt = 0
    }

    const transitionToDeletedDraft = () => {
        const deletedDraftId = draftId.value
        if (deletedDraftId != null && options.ownerId?.value != null) {
            markDraftDeletedLocally(options.ownerId.value, deletedDraftId)
        }
        clearAutosaveTimer()
        clearSaveRetry()
        resetDraftTracking()
        draftDeleted.value = true
        draftConflict.value = false
        draftProtected.value = false
        lastSaveFailed.value = false
        storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), null, null))
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
        options.onServerSaved?.(payload)
        if (generation !== sessionGeneration) return null
        draftId.value = savedDraft.draftId
        draftVersion.value = savedDraft.version ?? null
        clientDraftKey.value = savedDraft.clientDraftKey ?? clientDraftKey.value
        updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
        lastSavedAt.value = updatedAt.value
        lastSaveScope.value = 'server'
        if (revision === localRevision) {
            persistedRevision = revision
            storeLocalSnapshot(createStoredSavedDraftSnapshot(payload, savedDraft, updatedAt.value))
            options.onSaved?.()
        } else {
            storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), savedDraft.draftId, updatedAt.value))
        }
        lastSaveFailed.value = false
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
                if (isDraftMissingError(error) && draftId.value != null) {
                    transitionToDeletedDraft()
                }
                lastSaveFailed.value = !draftConflict.value && !draftProtected.value && !draftDeleted.value
                if (draftConflict.value) clearAutosaveTimer()
                if (draftProtected.value) clearAutosaveTimer()
                if (lastSaveFailed.value && isTransientDraftSaveError(error)) {
                    scheduleTransientSaveRetry()
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

    function scheduleTransientSaveRetry() {
        if (saveRetryTimer
            || saveRetryAttempt >= SAVE_RETRY_MAX_ATTEMPTS
            || !options.enabled.value
            || draftConflict.value
            || draftProtected.value
            || draftDeleted.value
            || options.canPersist?.() === false) return
        saveRetryAttempt++
        saveRetryTimer = setTimeout(() => {
            saveRetryTimer = null
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to retry draft autosave:', error)
            })
        }, getSaveRetryDelay(saveRetryAttempt))
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
            lastSaveFailed.value = false
            restoreFailed.value = false
            const latestSnapshot = latestDraft as unknown as DraftRecoverySnapshot
            options.applyDraft(latestSnapshot)
            storeLocalSnapshot({
                ...latestSnapshot,
                draftId: latestDraft.draftId,
                updatedAt: updatedAt.value ?? undefined,
                clientModifiedAt: new Date().toISOString(),
                hasLocalChanges: false,
            })
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

        try {
        cleanupExpiredDraftSnapshots()
        cleanupExpiredDraftTombstones()
        let localSnapshot = loadStoredDraftSnapshot(options.storageKey.value)
        if (isDraftDeletedLocally(options.ownerId?.value, localSnapshot?.draftId)) {
            Storage.remove(options.storageKey.value)
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
                resetDraftTracking()
                Storage.set(options.storageKey.value, snapshot)
            },
        })

        const recovery = resolveDraftRecoverySnapshot(resolved.localSnapshot, resolved.serverDraft)
        const chosen = recovery.snapshot
        restoreFailed.value = resolved.recoveryFailed
        if (!chosen) return
        if (generation !== sessionGeneration) return

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
        restoreSource.value = recovery.source
        options.applyDraft(chosen)
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
        await restoreDraft()
    }

    const clearRecovery = () => {
        invalidatePendingSaves()
        clearAutosaveTimer()
        clearSaveRetry()
        Storage.remove(options.storageKey.value)
        lastSaveScope.value = null
        draftDeleted.value = false
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
        restoreFailed.value = false
        isRestoringDraft.value = false
        draftConflict.value = false
        draftProtected.value = false
        draftDeleted.value = false
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
        if (lastSaveFailed.value) {
            clearSaveRetry()
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to retry draft autosave:', error)
            })
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
        if (event.key !== options.storageKey.value) return
        if (!event.newValue) {
            try {
                const removed = event.oldValue ? JSON.parse(event.oldValue) as DraftRecoverySnapshot : null
                if (removed?.clientInstanceId !== clientInstanceId
                    && removed?.draftId != null
                    && removed.draftId === draftId.value) {
                    transitionToDeletedDraft()
                }
            } catch (error: unknown) {
                logger.error('Failed to process a removed draft from another tab:', error)
            }
            return
        }
        try {
            const incoming = JSON.parse(event.newValue) as DraftRecoverySnapshot
            if (!incoming.clientInstanceId || incoming.clientInstanceId === clientInstanceId) return
            const sameDraft = incoming.draftId != null && incoming.draftId === draftId.value
            const serverAdvanced = sameDraft
                && (incoming.updatedAt ?? incoming.modifiedAt ?? null) !== updatedAt.value
            const matchingComposer = isMatchingLoadedDraft(incoming as DraftPost, options.buildPayload())
            const hasUnsavedLocalChanges = localRevision !== persistedRevision
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

    if (typeof window !== 'undefined') {
        window.addEventListener('online', handleOnline)
        window.addEventListener('storage', handleStorage)
    }

    const cleanupDraft = async () => {
        clearAutosaveTimer()
        clearSaveRetry()
        const currentDraftId = draftId.value
        if (currentDraftId == null) {
            clearRecovery()
            return
        }
        try {
            await deleteDraft(currentDraftId)
            clearRecovery()
        } catch (error: unknown) {
            if (isAxiosError(error) && error.response?.status === 404) {
                clearRecovery()
                return
            }
            logger.error('Failed to delete draft after publish:', error)
            throw error
        }
    }

    onUnmounted(() => {
        clearAutosaveTimer()
        clearSaveRetry()
        invalidatePendingSaves()
        if (typeof window !== 'undefined') {
            window.removeEventListener('online', handleOnline)
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
        lastLocalSaveFailed: computed(() => lastLocalSaveFailed.value),
        restoreFailed: computed(() => restoreFailed.value),
        isRestoringDraft: computed(() => isRestoringDraft.value),
        draftConflict: computed(() => draftConflict.value),
        draftProtected: computed(() => draftProtected.value),
        draftDeleted: computed(() => draftDeleted.value),
        isSavingDraft: computed(() => saveDraftMutation.isPending.value),
        restoreSource: computed(() => restoreSource.value),
        saveNow,
        scheduleAutosave,
        restoreDraft,
        retryRestore,
        reloadServerDraft,
        keepLocalDraft,
        resetSession,
        clearRecovery,
        cleanupDraft,
        writeLocalSnapshot,
        saveDeletedDraftAsNew,
        discardDeletedDraft,
    }
}
