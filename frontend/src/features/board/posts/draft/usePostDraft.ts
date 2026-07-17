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
    isDraftOutdatedError,
    isMatchingLoadedDraft,
    loadDraftById,
    pickNewestDraftSnapshot,
    type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'
import {
    createDraftRecoverySnapshot,
    createStoredSavedDraftSnapshot,
    hasBrowserDraftContent,
    hasMeaningfulDraftContent,
} from '@/features/board/posts/draft/postDraftSnapshot'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import { isDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'

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
}

const AUTOSAVE_DELAY_MS = 1500

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
    const updatedAt = ref<string | null>(null)
    const lastSavedAt = ref<string | null>(null)
    const lastSaveScope = ref<DraftSaveScope | null>(null)
    const lastSaveFailed = ref(false)
    const draftConflict = ref(false)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let savePromise: Promise<DraftPost | null> | null = null
    let saveQueued = false
    let localRevision = 0
    let sessionGeneration = 0

    const clearAutosaveTimer = () => {
        if (autosaveTimer) {
            clearTimeout(autosaveTimer)
            autosaveTimer = null
        }
    }

    const resetDraftTracking = () => {
        draftId.value = null
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
    }

    const storeLocalSnapshot = (snapshot: DraftRecoverySnapshot) => {
        Storage.set(options.storageKey.value, snapshot)
    }

    const writeLocalSnapshot = () => {
        if (!options.enabled.value) return
        localRevision++
        const snapshot = createDraftRecoverySnapshot(options.buildPayload(), draftId.value, updatedAt.value)
        storeLocalSnapshot(snapshot)
    }

    const savePayload = async (payload: PostDraftData) => {
        requestController?.abort()
        const controller = new AbortController()
        requestController = controller
        try {
            return await saveDraftMutation.mutateAsync({
                ...payload,
                draftId: draftId.value ?? undefined,
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
            storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), null, null))
            if (hasBrowserDraftContent(payload)) {
                lastSavedAt.value = new Date().toISOString()
                lastSaveScope.value = 'browser'
            } else {
                lastSaveScope.value = null
            }
            lastSaveFailed.value = false
            options.onSaved?.()
            return null
        }

        storeLocalSnapshot(createDraftRecoverySnapshot(payload, draftId.value, updatedAt.value))
        const savedDraft = unwrapAxiosApiData(await savePayload(payload))
        if (generation !== sessionGeneration) return null
        draftId.value = savedDraft.draftId
        updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
        lastSavedAt.value = updatedAt.value
        lastSaveScope.value = 'server'
        if (revision === localRevision) {
            storeLocalSnapshot(createStoredSavedDraftSnapshot(payload, savedDraft, updatedAt.value))
            options.onSaved?.()
        } else {
            storeLocalSnapshot(createDraftRecoverySnapshot(options.buildPayload(), savedDraft.draftId, updatedAt.value))
        }
        lastSaveFailed.value = false
        return savedDraft
    }

    const saveNow = async () => {
        if (draftConflict.value) return null
        if (savePromise) {
            saveQueued = true
            return savePromise
        }
        const generation = sessionGeneration
        const pendingSave = (async () => {
            let savedDraft: DraftPost | null = null
            do {
                saveQueued = false
                savedDraft = await persistNow()
            } while (saveQueued && generation === sessionGeneration && options.enabled.value)
            return savedDraft
        })().catch((error: unknown) => {
            if (generation === sessionGeneration) {
                draftConflict.value = isDraftOutdatedError(error)
                lastSaveFailed.value = !draftConflict.value
                if (draftConflict.value) clearAutosaveTimer()
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

    const scheduleAutosave = () => {
        if (!options.enabled.value || draftConflict.value) return
        clearAutosaveTimer()
        autosaveTimer = setTimeout(() => {
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to autosave draft:', error)
            })
        }, AUTOSAVE_DELAY_MS)
    }

    const reloadServerDraft = async () => {
        const generation = sessionGeneration
        const currentDraftId = draftId.value
        if (currentDraftId == null) return false
        const latestDraft = await loadDraftById(currentDraftId)
        if (generation !== sessionGeneration || draftId.value !== currentDraftId) return false
        if (!isMatchingLoadedDraft(latestDraft, options.buildPayload())) return false
        draftId.value = latestDraft.draftId
        updatedAt.value = getDraftUpdatedAt(latestDraft)
        lastSavedAt.value = updatedAt.value
        lastSaveScope.value = 'server'
        draftConflict.value = false
        lastSaveFailed.value = false
        const latestSnapshot = latestDraft as unknown as DraftRecoverySnapshot
        options.applyDraft(latestSnapshot)
        storeLocalSnapshot({
            ...latestSnapshot,
            draftId: latestDraft.draftId,
            updatedAt: updatedAt.value ?? undefined,
        })
        options.onSaved?.()
        return true
    }

    const restoreDraft = async () => {
        if (hasRestoredDraft.value || !options.enabled.value) return
        const generation = sessionGeneration
        hasRestoredDraft.value = true

        let localSnapshot = Storage.get<DraftRecoverySnapshot>(options.storageKey.value, null)
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

        const chosen = pickNewestDraftSnapshot(resolved.localSnapshot, resolved.serverDraft)
        if (!chosen) return
        if (generation !== sessionGeneration) return

        draftId.value = chosen.draftId ?? null
        updatedAt.value = chosen.updatedAt ?? chosen.modifiedAt ?? null
        restoreSource.value = chosen === localSnapshot ? 'local' : 'server'
        options.applyDraft(chosen)
        writeLocalSnapshot()
    }

    const clearRecovery = () => {
        invalidatePendingSaves()
        clearAutosaveTimer()
        Storage.remove(options.storageKey.value)
        lastSaveScope.value = null
        resetDraftTracking()
        restoreSource.value = 'idle'
    }

    const resetSession = () => {
        invalidatePendingSaves()
        clearAutosaveTimer()
        lastSaveScope.value = null
        lastSaveFailed.value = false
        draftConflict.value = false
        resetDraftTracking()
        restoreSource.value = 'idle'
        hasRestoredDraft.value = false
    }

    watch(options.enabled, (enabled) => {
        if (!enabled) {
            clearAutosaveTimer()
        }
    })

    const cleanupDraft = async () => {
        clearAutosaveTimer()
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
        invalidatePendingSaves()
    })

    return {
        draftId,
        updatedAt,
        lastSavedAt: computed(() => lastSavedAt.value),
        lastSaveScope: computed(() => lastSaveScope.value),
        lastSaveFailed: computed(() => lastSaveFailed.value),
        draftConflict: computed(() => draftConflict.value),
        isSavingDraft: computed(() => saveDraftMutation.isPending.value),
        restoreSource: computed(() => restoreSource.value),
        saveNow,
        scheduleAutosave,
        restoreDraft,
        reloadServerDraft,
        resetSession,
        clearRecovery,
        cleanupDraft,
        writeLocalSnapshot,
    }
}
