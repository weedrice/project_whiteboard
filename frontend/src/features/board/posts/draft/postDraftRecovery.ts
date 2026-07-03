import { isAxiosError } from 'axios'
import { postApi, type PostDraftData } from '@/api/post'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import type { DraftPost, DraftPostSummary } from '@/types'

export interface DraftRecoverySnapshot extends PostDraftData {
    draftId?: number
    categoryId?: number | null
    modifiedAt?: string
}

type ApiErrorPayload = {
    code?: string
    error?: {
        code?: string
    }
}

const DRAFT_OUTDATED_ERROR_CODE = 'P004'

export const toIsoTime = (value?: string | null): string | null => {
    if (!value) return null
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return null
    return parsed.toISOString()
}

export const pickNewestDraftSnapshot = (
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

export const isMatchingLoadedDraft = (draft: DraftPost, payload: PostDraftData) => {
    if (draft.boardUrl !== payload.boardUrl) return false
    const draftOriginalPostId = draft.originalPostId ?? null
    const payloadOriginalPostId = payload.originalPostId ?? null
    return draftOriginalPostId === payloadOriginalPostId
}

export const getDraftUpdatedAt = (draft: Pick<DraftPost, 'updatedAt' | 'modifiedAt'>): string | null => (
    draft.updatedAt ?? draft.modifiedAt ?? null
)

export const isDraftOutdatedError = (error: unknown): boolean => {
    if (!isAxiosError(error) || error.response?.status !== 409) {
        return false
    }
    const data = error.response.data as ApiErrorPayload | undefined
    return data?.error?.code === DRAFT_OUTDATED_ERROR_CODE || data?.code === DRAFT_OUTDATED_ERROR_CODE
}

export const findMatchingServerDraftId = async (payload: PostDraftData): Promise<number | null> => {
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

export const loadDraftById = async (draftId: number) => (
    unwrapAxiosApiData(await postApi.getDraft(draftId))
)
