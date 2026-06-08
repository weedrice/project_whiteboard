import { computed, onUnmounted, ref, type Ref } from 'vue'
import { isAxiosError } from 'axios'
import { postApi, type PostDraftData } from '@/api/post'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { usePost } from '@/composables/usePost'
import type { DraftPost, DraftPostSummary } from '@/types'
import { Storage } from '@/utils/storage'
import logger from '@/utils/logger'

export interface DraftRecoverySnapshot extends PostDraftData {
    draftId?: number
    categoryId?: number | null
    modifiedAt?: string
}

interface UsePostDraftOptions {
    enabled: Ref<boolean>
    storageKey: Ref<string>
    buildPayload: () => PostDraftData
    applyDraft: (draft: DraftRecoverySnapshot) => void
}

const AUTOSAVE_DELAY_MS = 1500
const DRAFT_OUTDATED_ERROR_CODE = 'P004'

type ApiErrorPayload = {
    code?: string
    error?: {
        code?: string
    }
}

const toIsoTime = (value?: string | null): string | null => {
    if (!value) return null
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return null
    return parsed.toISOString()
}

const pickNewestSnapshot = (
    localSnapshot: DraftRecoverySnapshot | null,
    serverDraft: DraftPost | null,
): DraftRecoverySnapshot | null => {
    if (!localSnapshot && !serverDraft) return null
    if (!localSnapshot) return serverDraft as DraftRecoverySnapshot
    if (!serverDraft) return localSnapshot

    const localUpdatedAt = toIsoTime(localSnapshot.updatedAt)
    const serverUpdatedAt = toIsoTime(serverDraft.updatedAt ?? serverDraft.modifiedAt)
    if (!localUpdatedAt) return serverDraft as DraftRecoverySnapshot
    if (!serverUpdatedAt) return localSnapshot
    return localUpdatedAt >= serverUpdatedAt ? localSnapshot : serverDraft as DraftRecoverySnapshot
}

const isMatchingDraft = (draft: DraftPostSummary, payload: PostDraftData) => {
    if (draft.boardUrl !== payload.boardUrl) return false
    const draftOriginalPostId = draft.originalPostId ?? null
    const payloadOriginalPostId = payload.originalPostId ?? null
    return draftOriginalPostId === payloadOriginalPostId
}

const isMatchingLoadedDraft = (draft: DraftPost, payload: PostDraftData) => {
    if (draft.boardUrl !== payload.boardUrl) return false
    const draftOriginalPostId = draft.originalPostId ?? null
    const payloadOriginalPostId = payload.originalPostId ?? null
    return draftOriginalPostId === payloadOriginalPostId
}

const getDraftUpdatedAt = (draft: Pick<DraftPost, 'updatedAt' | 'modifiedAt'>): string | null => (
    draft.updatedAt ?? draft.modifiedAt ?? null
)

const isDraftOutdatedError = (error: unknown): boolean => {
    if (!isAxiosError(error) || error.response?.status !== 409) {
        return false
    }
    const data = error.response.data as ApiErrorPayload | undefined
    return data?.error?.code === DRAFT_OUTDATED_ERROR_CODE || data?.code === DRAFT_OUTDATED_ERROR_CODE
}

const findMatchingServerDraftId = async (payload: PostDraftData): Promise<number | null> => {
    let page = 0
    let hasNext = true
    const matchingCreateDraftIds: number[] = []
    const payloadOriginalPostId = payload.originalPostId ?? null

    while (hasNext) {
        const response = unwrapAxiosApiData(await userApi.getMyDrafts({ page, size: 50 }))
        const drafts = response.content ?? []

        for (const draft of drafts) {
            if (!isMatchingDraft(draft, payload)) {
                continue
            }

            if (payloadOriginalPostId != null) {
                return draft.draftId
            }

            if (draft.draftId != null) {
                matchingCreateDraftIds.push(draft.draftId)
            }
        }
        hasNext = response.hasNext ?? false
        page += 1
    }

    return matchingCreateDraftIds.length === 1 ? matchingCreateDraftIds[0] : null
}

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
        const snapshot: DraftRecoverySnapshot = {
            ...options.buildPayload(),
            draftId: draftId.value ?? undefined,
            updatedAt: updatedAt.value ?? undefined,
        }
        Storage.set(options.storageKey.value, snapshot)
    }

    const refreshCurrentDraftVersion = async (payload: PostDraftData): Promise<string | null> => {
        const currentDraftId = draftId.value
        if (currentDraftId == null) {
            return null
        }
        const latestDraft = unwrapAxiosApiData(await postApi.getDraft(currentDraftId))
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
        const hasMeaningfulContent = Boolean(
            payload.title?.trim()
            || payload.contents?.trim()
            || payload.tags?.length
            || payload.fileIds?.length,
        )
        if (!hasMeaningfulContent) {
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
        Storage.set(options.storageKey.value, {
            ...payload,
            draftId: draftId.value,
            updatedAt: updatedAt.value ?? undefined,
        })
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

        let localSnapshot = Storage.get<DraftRecoverySnapshot>(options.storageKey.value, null)
        let serverDraft: DraftPost | null = null
        let serverDraftId = localSnapshot?.draftId ?? null
        const payload = options.buildPayload()

        if (serverDraftId == null) {
            try {
                serverDraftId = await findMatchingServerDraftId(payload)
            } catch (error: unknown) {
                logger.error('Failed to resolve server draft id:', error)
            }
        }

        if (serverDraftId != null) {
            try {
                const data = await postApi.getDraft(serverDraftId)
                if (generation !== sessionGeneration) return
                serverDraft = unwrapAxiosApiData(data)
            } catch (error: unknown) {
                if (generation !== sessionGeneration) return
                if (
                    localSnapshot?.draftId === serverDraftId
                    && isAxiosError(error)
                    && error.response?.status === 404
                ) {
                    localSnapshot = {
                        ...localSnapshot,
                        draftId: undefined,
                        updatedAt: undefined,
                        modifiedAt: undefined,
                    }
                    resetDraftTracking()
                    Storage.set(options.storageKey.value, localSnapshot)
                    serverDraftId = null
                    try {
                        const fallbackDraftId = await findMatchingServerDraftId(payload)
                        if (fallbackDraftId != null) {
                            serverDraft = unwrapAxiosApiData(await postApi.getDraft(fallbackDraftId))
                        }
                    } catch (resolveError: unknown) {
                        logger.error('Failed to restore replacement server draft:', resolveError)
                    }
                }
                logger.error('Failed to restore server draft:', error)
            }
        }

        const chosen = pickNewestSnapshot(localSnapshot, serverDraft)
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
