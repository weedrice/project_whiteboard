import { isAxiosError } from 'axios'
import { postApi, type PostDraftData } from '@/api/post'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { API_ERROR_CODES } from '@/api/errorCodes'
import type { DraftPost, DraftPostSummary } from '@/types'
import { withServerOffset } from '@/utils/date'

export interface DraftRecoverySnapshot extends PostDraftData {
    draftId?: number
    categoryId?: number | null
    modifiedAt?: string
    clientModifiedAt?: string
    clientInstanceId?: string
    hasLocalChanges?: boolean
}

export interface DraftRecoveryResolution {
    snapshot: DraftRecoverySnapshot | null
    source: 'local' | 'server' | 'idle'
    conflict: boolean
}

type ApiErrorPayload = {
    code?: string
    error?: {
        code?: string
    }
}

const DRAFT_OUTDATED_ERROR_CODE = API_ERROR_CODES.DRAFT_OUTDATED
const DRAFT_PROTECTED_ERROR_CODE = API_ERROR_CODES.DRAFT_PROTECTED

export const toIsoTime = (value?: string | null): string | null => {
    if (!value) return null
    // 서버 값과 기기에 저장된 값을 함께 비교하므로 해석 기준을 맞춰야 한다.
    // 기준이 다르면 오래된 로컬 스냅샷이 최신 서버본을 이길 수 있다.
    const parsed = new Date(withServerOffset(value))
    if (Number.isNaN(parsed.getTime())) return null
    return parsed.toISOString()
}

export const resolveDraftRecoverySnapshot = (
    localSnapshot: DraftRecoverySnapshot | null,
    serverDraft: DraftPost | null,
): DraftRecoveryResolution => {
    if (!localSnapshot && !serverDraft) {
        return { snapshot: null, source: 'idle', conflict: false }
    }
    if (!localSnapshot) {
        return { snapshot: serverDraft as DraftRecoverySnapshot, source: 'server', conflict: false }
    }
    if (!serverDraft) {
        return { snapshot: localSnapshot, source: 'local', conflict: false }
    }

    if (localSnapshot.hasLocalChanges === false) {
        return { snapshot: serverDraft as DraftRecoverySnapshot, source: 'server', conflict: false }
    }

    const localUpdatedAt = toIsoTime(localSnapshot.updatedAt)
    const serverUpdatedAt = toIsoTime(serverDraft.updatedAt ?? serverDraft.modifiedAt)
    if (localUpdatedAt && serverUpdatedAt && localUpdatedAt === serverUpdatedAt) {
        return { snapshot: localSnapshot, source: 'local', conflict: false }
    }

    // 로컬 내용이 어느 서버 버전에서 갈라졌는지 확인할 수 없거나 서버도 갱신되었다면
    // 한쪽을 자동으로 버리지 않고 로컬 내용을 보존한 채 사용자가 선택하도록 한다.
    return { snapshot: localSnapshot, source: 'local', conflict: true }
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

export const isDraftProtectedError = (error: unknown): boolean => {
    if (!isAxiosError(error) || error.response?.status !== 409) {
        return false
    }
    const data = error.response.data as ApiErrorPayload | undefined
    return data?.error?.code === DRAFT_PROTECTED_ERROR_CODE || data?.code === DRAFT_PROTECTED_ERROR_CODE
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
