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
    hasMeaningfulDraftContent,
} from '@/features/board/posts/draft/postDraftSnapshot'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'

export type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

interface UsePostDraftOptions {
    enabled: Ref<boolean>
    storageKey: Ref<string>
    preferredDraftId?: Ref<number | null>
    buildPayload: () => PostDraftData
    applyDraft: (draft: DraftRecoverySnapshot) => void
}

const AUTOSAVE_DELAY_MS = 1500

export function usePostDraft(options: UsePostDraftOptions) {
    const { useSaveDraft, useDeleteDraft } = usePost()
    if (typeof useSaveDraft !== 'function' || typeof useDeleteDraft !== 'function') {
        throw new Error('Draft mutations are not available.')
    }
    const saveDraftMutation = useSaveDraft()
    const deleteDraftMutation = useDeleteDraft()

    const draftId = ref<number | null>(null)
    const updatedAt = ref<string | null>(null)
    const lastSavedAt = ref<string | null>(null)
    const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
    const hasRestoredDraft = ref(false)
    let autosaveTimer: ReturnType<typeof setTimeout> | null = null
    let savePromise: Promise<DraftPost | null> | null = null
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

    const writeLocalSnapshot = () => {
        if (!options.enabled.value) return
        const snapshot = createDraftRecoverySnapshot(options.buildPayload(), draftId.value, updatedAt.value)
        Storage.set(options.storageKey.value, snapshot)
    }

    const refreshCurrentDraftVersion = async (payload: PostDraftData): Promise<string | null> => {
        const currentDraftId = draftId.value
        if (currentDraftId == null) {
            return null
        }
        const latestDraft = await loadDraftById(currentDraftId)
        if (!isMatchingLoadedDraft(latestDraft, payload)) {
            return null
        }
        draftId.value = latestDraft.draftId
        updatedAt.value = getDraftUpdatedAt(latestDraft)
        writeLocalSnapshot()
        return updatedAt.value
    }

    const savePayload = async (payload: PostDraftData, allowVersionRefresh = true) => {
        try {
            return await saveDraftMutation.mutateAsync({
                ...payload,
                draftId: draftId.value ?? undefined,
                updatedAt: updatedAt.value ?? undefined,
            })
        } catch (error: unknown) {
            if (!allowVersionRefresh || !isDraftOutdatedError(error)) {
                throw error
            }
            let latestUpdatedAt: string | null = null
            try {
                latestUpdatedAt = await refreshCurrentDraftVersion(payload)
            } catch (refreshError: unknown) {
                logger.error('Failed to refresh outdated draft version:', refreshError)
            }
            if (!latestUpdatedAt) {
                throw error
            }
            return await saveDraftMutation.mutateAsync({
                ...payload,
                draftId: draftId.value ?? undefined,
                updatedAt: latestUpdatedAt,
            })
        }
    }

    const persistNow = async () => {
        if (!options.enabled.value) return null
        const generation = sessionGeneration
        clearAutosaveTimer()
        const payload = options.buildPayload()
        if (!hasMeaningfulDraftContent(payload)) {
            const existingDraftId = draftId.value
            if (existingDraftId != null) {
                try {
                    await deleteDraftMutation.mutateAsync(existingDraftId)
                } catch (error: unknown) {
                    logger.error('Failed to delete empty draft:', error)
                    throw error
                }
            }
            if (generation !== sessionGeneration) return null
            resetDraftTracking()
            writeLocalSnapshot()
            return null
        }

        writeLocalSnapshot()
        const savedDraft = unwrapAxiosApiData(await savePayload(payload))
        if (generation !== sessionGeneration) return null
        draftId.value = savedDraft.draftId
        updatedAt.value = getDraftUpdatedAt(savedDraft) ?? new Date().toISOString()
        lastSavedAt.value = updatedAt.value
        Storage.set(options.storageKey.value, createStoredSavedDraftSnapshot(payload, savedDraft, updatedAt.value))
        return savedDraft
    }

    const saveNow = async () => {
        if (savePromise) {
            return savePromise
        }
        savePromise = persistNow().finally(() => {
            savePromise = null
        })
        return savePromise
    }

    const scheduleAutosave = () => {
        if (!options.enabled.value) return
        clearAutosaveTimer()
        autosaveTimer = setTimeout(() => {
            void saveNow().catch((error: unknown) => {
                logger.error('Failed to autosave draft:', error)
            })
        }, AUTOSAVE_DELAY_MS)
    }

    const restoreDraft = async () => {
        if (hasRestoredDraft.value || !options.enabled.value) return
        const generation = sessionGeneration
        hasRestoredDraft.value = true

        const localSnapshot = Storage.get<DraftRecoverySnapshot>(options.storageKey.value, null)
        const payload = options.buildPayload()
        const resolved = await resolveServerDraftForRecovery({
            payload,
            localSnapshot,
            preferredDraftId: options.preferredDraftId?.value ?? null,
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
        clearAutosaveTimer()
        Storage.remove(options.storageKey.value)
        resetDraftTracking()
        restoreSource.value = 'idle'
    }

    const resetSession = () => {
        sessionGeneration++
        clearAutosaveTimer()
        savePromise = null
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
            await deleteDraftMutation.mutateAsync(currentDraftId)
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
    })

    return {
        draftId,
        updatedAt,
        lastSavedAt: computed(() => lastSavedAt.value),
        isSavingDraft: computed(() => saveDraftMutation.isPending.value),
        restoreSource: computed(() => restoreSource.value),
        saveNow,
        scheduleAutosave,
        restoreDraft,
        resetSession,
        clearRecovery,
        cleanupDraft,
        writeLocalSnapshot,
    }
}
