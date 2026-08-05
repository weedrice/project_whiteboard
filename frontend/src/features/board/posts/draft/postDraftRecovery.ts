import { isAxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { postApi, type PostDraftData } from '@/api/post'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { API_ERROR_CODES } from '@/api/errorCodes'
import type { DraftPost } from '@/types'
import { withServerOffset } from '@/utils/date'

export interface DraftRecoverySnapshot extends PostDraftData {
    schemaVersion?: number
    draftId?: number
    categoryId?: number | null
    modifiedAt?: string
    clientModifiedAt?: string
    clientInstanceId?: string
    hasLocalChanges?: boolean
    staleReferencesReset?: boolean
    contractValidationFailed?: boolean
    unassociatedUploadFileIds?: number[]
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
const DRAFT_NOT_FOUND_ERROR_CODE = API_ERROR_CODES.DRAFT_NOT_FOUND

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

export const isMatchingLoadedDraft = (draft: DraftPost, payload: PostDraftData) => {
    if (draft.boardUrl !== payload.boardUrl) return false
    const draftOriginalPostId = draft.originalPostId ?? null
    const payloadOriginalPostId = payload.originalPostId ?? null
    return draftOriginalPostId === payloadOriginalPostId
}

type DraftContentSource = PostDraftData | DraftPost

const normalizeDraftContent = (draft: DraftContentSource) => ({
    title: draft.title ?? '',
    contents: draft.contents ?? '',
    categoryId: draft.categoryId ?? null,
    tags: [...(draft.tags ?? [])].sort(),
    isNotice: draft.isNotice ?? false,
    isNsfw: draft.isNsfw ?? false,
    isSpoiler: draft.isSpoiler ?? false,
    isSecret: draft.isSecret ?? false,
    fileIds: [...(draft.fileIds ?? [])].sort((left, right) => left - right),
    poll: draft.poll
        ? {
            question: draft.poll.question,
            options: draft.poll.options,
            multipleChoiceEnabled: draft.poll.multipleChoiceEnabled ?? false,
            anonymousEnabled: draft.poll.anonymousEnabled ?? false,
            closesAt: draft.poll.closesAt ?? null,
        }
        : null,
    seriesId: draft.seriesId ?? null,
})

const serializeDraftContent = (draft: DraftContentSource): string => (
    JSON.stringify(normalizeDraftContent(draft))
)

export const createDraftContentFingerprint = (draft: DraftContentSource): string => {
    const serialized = serializeDraftContent(draft)
    let fnv = 0x811c9dc5
    let djb = 5381
    for (let index = 0; index < serialized.length; index++) {
        const code = serialized.charCodeAt(index)
        fnv = Math.imul(fnv ^ code, 0x01000193)
        djb = Math.imul(djb, 33) ^ code
    }
    return `${serialized.length.toString(36)}:${(fnv >>> 0).toString(36)}:${(djb >>> 0).toString(36)}`
}

export const hasSameDraftContent = (left: DraftContentSource, right: DraftContentSource): boolean => (
    serializeDraftContent(left) === serializeDraftContent(right)
)

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

export const isDraftMissingError = (error: unknown): boolean => {
    if (!isAxiosError(error) || error.response?.status !== 404) return false
    const data = error.response.data as ApiErrorPayload | undefined
    return data?.error?.code === DRAFT_NOT_FOUND_ERROR_CODE || data?.code === DRAFT_NOT_FOUND_ERROR_CODE
}

export interface MatchingServerDraftResolution {
    draftId: number | null
    multipleMatchesFound: boolean
}

export const resolveMatchingServerDraft = async (
    payload: PostDraftData,
    config?: AxiosRequestConfig,
): Promise<MatchingServerDraftResolution> => {
    return unwrapAxiosApiData(await userApi.getMatchingDraft({
        boardUrl: payload.boardUrl,
        ...(payload.originalPostId != null ? { originalPostId: payload.originalPostId } : {}),
        ...(payload.clientDraftKey ? { clientDraftKey: payload.clientDraftKey } : {}),
    }, config))
}

export const findMatchingServerDraftId = async (payload: PostDraftData): Promise<number | null> => (
    (await resolveMatchingServerDraft(payload)).draftId
)

export const loadDraftById = async (draftId: number, config?: AxiosRequestConfig) => (
    unwrapAxiosApiData(await postApi.getDraft(draftId, config))
)
